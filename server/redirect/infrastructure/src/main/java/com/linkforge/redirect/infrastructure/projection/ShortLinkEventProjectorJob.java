package com.linkforge.redirect.infrastructure.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.shortlink.ShortLinkEventTypes;
import com.linkforge.contract.shortlink.ShortLinkPublicSnapshot;
import com.linkforge.contract.shortlink.event.ShortLinkArchivedV1;
import com.linkforge.contract.shortlink.event.ShortLinkCreatedV1;
import com.linkforge.contract.shortlink.event.ShortLinkDeletedV1;
import com.linkforge.contract.shortlink.event.ShortLinkRestoredV1;
import com.linkforge.contract.shortlink.event.ShortLinkUpdatedV1;
import com.linkforge.foundation.eventing.IntegrationCheckpointRepository;
import com.linkforge.foundation.eventing.IntegrationDeadLetterRepository;
import com.linkforge.foundation.eventing.IntegrationEventRow;
import com.linkforge.foundation.eventing.IntegrationEventStore;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class ShortLinkEventProjectorJob {

    private static final Logger log = LoggerFactory.getLogger(ShortLinkEventProjectorJob.class);

    private static final String CONSUMER = "redirect-shortlink-projector";

    private final IntegrationEventStore eventStore;
    private final IntegrationCheckpointRepository checkpoints;
    private final IntegrationDeadLetterRepository deadLetters;
    private final RedirectLinkProjectionMapper projectionMapper;
    private final LinkCachePort linkCache;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final int batchSize;

    public ShortLinkEventProjectorJob(
            IntegrationEventStore eventStore,
            IntegrationCheckpointRepository checkpoints,
            IntegrationDeadLetterRepository deadLetters,
            RedirectLinkProjectionMapper projectionMapper,
            LinkCachePort linkCache,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${APP_REDIRECT_SHORTLINK_PROJECTOR_BATCH_SIZE:200}") int batchSize
    ) {
        this.eventStore = eventStore;
        this.checkpoints = checkpoints;
        this.deadLetters = deadLetters;
        this.projectionMapper = projectionMapper;
        this.linkCache = linkCache;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
        this.batchSize = normalizeBatchSize(batchSize);
    }

    @Scheduled(fixedDelayString = "${APP_REDIRECT_SHORTLINK_PROJECTOR_DELAY_MS:1000}")
    @SchedulerLock(name = "lf:job:redirect-shortlink-projector", lockAtMostFor = "PT2M")
    public void drain() {
        long lastSeq = checkpoints.loadOrInit(CONSUMER);
        List<IntegrationEventRow> events = eventStore.listAfterSeq(lastSeq, batchSize);
        if (events.isEmpty()) {
            return;
        }
        for (IntegrationEventRow event : events) {
            if (event == null) {
                continue;
            }
            try {
                ProjectAction action = projectOne(event);
                applyCacheSideEffects(action);
                checkpoint(event.seq());
            } catch (PoisonEventException ex) {
                deadLetterAndSkip(event, ex.getMessage());
            } catch (Exception ex) {
                // Transient failure: do not advance checkpoint; retry in next loop.
                log.debug(
                        "redirect projector failed: consumer={}, seq={}, eventId={}, eventType={}, err={}",
                        CONSUMER,
                        event.seq(),
                        event.eventId(),
                        event.eventType(),
                        ex.getMessage()
                );
                return;
            }
        }
    }

    private ProjectAction projectOne(IntegrationEventRow event) {
        String eventType = event.eventType();
        if (eventType == null || eventType.isBlank()) {
            return ProjectAction.none();
        }

        if (!eventType.startsWith("shortlink.")) {
            return ProjectAction.none();
        }

        try {
            return switch (eventType) {
                case ShortLinkEventTypes.SHORT_LINK_CREATED_V1 ->
                        upsert(event, read(event.payloadJson(), ShortLinkCreatedV1.class).snapshot());
                case ShortLinkEventTypes.SHORT_LINK_UPDATED_V1 ->
                        upsert(event, read(event.payloadJson(), ShortLinkUpdatedV1.class).snapshot());
                case ShortLinkEventTypes.SHORT_LINK_RESTORED_V1 ->
                        upsert(event, read(event.payloadJson(), ShortLinkRestoredV1.class).snapshot());
                case ShortLinkEventTypes.SHORT_LINK_ARCHIVED_V1 ->
                        delete(event, read(event.payloadJson(), ShortLinkArchivedV1.class).snapshot());
                case ShortLinkEventTypes.SHORT_LINK_DELETED_V1 ->
                        delete(event, read(event.payloadJson(), ShortLinkDeletedV1.class).snapshot());
                default -> ProjectAction.none();
            };
        } catch (JsonProcessingException ex) {
            throw new PoisonEventException("invalid payload_json: " + ex.getOriginalMessage(), ex);
        }
    }

    private ProjectAction upsert(IntegrationEventRow event, ShortLinkPublicSnapshot snapshot) {
        if (snapshot == null || snapshot.code() == null || snapshot.code().isBlank()) {
            throw new PoisonEventException("snapshot/code is required");
        }
        if (snapshot.originalUrl() == null || snapshot.originalUrl().isBlank()) {
            throw new PoisonEventException("snapshot/originalUrl is required");
        }

        if (snapshot.hostname() == null || snapshot.hostname().isBlank()) {
            return ProjectAction.cacheOnly(toMeta(snapshot));
        }

        RedirectLinkProjection row = toRow(snapshot);
        return tx.execute(status -> {
            projectionMapper.upsert(row);
            return ProjectAction.put(row);
        });
    }

    private ProjectAction delete(IntegrationEventRow event, ShortLinkPublicSnapshot snapshot) {
        if (snapshot == null || snapshot.code() == null || snapshot.code().isBlank()) {
            throw new PoisonEventException("snapshot/code is required");
        }

        if (snapshot.hostname() == null || snapshot.hostname().isBlank()) {
            return ProjectAction.evict(null, snapshot.code().trim());
        }

        String normalizedHost = snapshot.hostname().trim().toLowerCase();
        String normalizedCode = snapshot.code().trim();
        return tx.execute(status -> {
            projectionMapper.deleteByHostnameAndCode(normalizedHost, normalizedCode);
            return ProjectAction.evict(normalizedHost, normalizedCode);
        });
    }

    private void checkpoint(long seq) {
        tx.executeWithoutResult(status -> checkpoints.update(CONSUMER, seq));
    }

    private void deadLetterAndSkip(IntegrationEventRow event, String lastError) {
        tx.executeWithoutResult(status -> {
            deadLetters.upsertFailure(CONSUMER, event, 1, safeError(lastError));
            checkpoints.update(CONSUMER, event.seq());
        });
    }

    private void applyCacheSideEffects(ProjectAction action) {
        if (action == null) {
            return;
        }
        if (action.evictCode != null) {
            boolean ok = action.evictHostname == null
                    ? linkCache.tryEvict(action.evictCode)
                    : linkCache.tryEvict(action.evictHostname, action.evictCode);
            tryCache("evict", action.evictHostname, action.evictCode, ok);
        }
        if (action.putRow != null) {
            LinkMeta meta = RedirectLinkProjectionQueryService.toMeta(action.putRow);
            // overwrite negative cache sentinel if any
            tryCache("evict", meta.hostname(), meta.code(), linkCache.tryEvict(meta.hostname(), meta.code()));
            tryCache("put", meta.hostname(), meta.code(), linkCache.tryPut(meta.hostname(), meta));
        }
        if (action.cacheMeta != null) {
            LinkMeta meta = action.cacheMeta;
            if (meta.hostname() == null || meta.hostname().isBlank()) {
                tryCache("evict", null, meta.code(), linkCache.tryEvict(meta.code()));
                tryCache("put", null, meta.code(), linkCache.tryPut(meta));
            } else {
                tryCache("evict", meta.hostname(), meta.code(), linkCache.tryEvict(meta.hostname(), meta.code()));
                tryCache("put", meta.hostname(), meta.code(), linkCache.tryPut(meta.hostname(), meta));
            }
        }
    }

    private static void tryCache(String op, String hostname, String code, boolean ok) {
        if (ok) {
            return;
        }
        throw new CacheSideEffectException("cache " + op + " failed: host=" + hostname + ", code=" + code);
    }

    private <T> T read(String json, Class<T> type) throws JsonProcessingException {
        return objectMapper.readValue(json, type);
    }

    private static RedirectLinkProjection toRow(ShortLinkPublicSnapshot s) {
        RedirectLinkProjection row = new RedirectLinkProjection();
        row.setHostname(s.hostname().trim().toLowerCase());
        row.setCode(s.code());
        row.setTenantId(s.tenantId());
        row.setLinkId(s.linkId());
        row.setOriginalUrl(s.originalUrl());
        row.setEnabled(s.enabled());
        row.setExpiresAt(toUtcLocalDateTime(s.expiresAtUtc()));
        row.setRedirectStatusCode(s.redirectStatusCode());
        row.setPreviewEnabled(s.previewEnabled());
        row.setUnavailableLandingUrl(s.unavailableLandingUrl());
        row.setQueryForwardMode(s.queryForwardMode());
        row.setQueryForwardAllowlist(joinAllowlist(s.queryForwardAllowlist()));
        return row;
    }

    private static LinkMeta toMeta(ShortLinkPublicSnapshot s) {
        String hostname = s.hostname();
        if (hostname != null && !hostname.isBlank()) {
            hostname = hostname.trim().toLowerCase();
        } else {
            hostname = null;
        }
        return new LinkMeta(
                s.linkId(),
                s.tenantId(),
                s.code(),
                s.originalUrl(),
                s.enabled(),
                toUtcLocalDateTime(s.expiresAtUtc()),
                s.redirectStatusCode(),
                s.previewEnabled(),
                s.unavailableLandingUrl(),
                s.queryForwardMode(),
                joinAllowlist(s.queryForwardAllowlist()),
                hostname
        );
    }

    private static LocalDateTime toUtcLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String joinAllowlist(List<String> allowlist) {
        if (allowlist == null || allowlist.isEmpty()) {
            return null;
        }
        String joined = String.join(",", allowlist.stream().filter(s -> s != null && !s.isBlank()).toList());
        return joined.isBlank() ? null : joined;
    }

    private static int normalizeBatchSize(int raw) {
        if (raw <= 0) {
            return 200;
        }
        return Math.max(1, Math.min(raw, 1000));
    }

    private static String safeError(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > 512 ? t.substring(0, 512) : t;
    }

    private record ProjectAction(String evictHostname, String evictCode, RedirectLinkProjection putRow, LinkMeta cacheMeta) {
        static ProjectAction none() {
            return new ProjectAction(null, null, null, null);
        }

        static ProjectAction evict(String hostname, String code) {
            return new ProjectAction(hostname, code, null, null);
        }

        static ProjectAction put(RedirectLinkProjection row) {
            return new ProjectAction(null, null, row, null);
        }

        static ProjectAction cacheOnly(LinkMeta meta) {
            return new ProjectAction(null, null, null, meta);
        }
    }

    private static final class PoisonEventException extends RuntimeException {
        private PoisonEventException(String message) {
            super(message);
        }

        private PoisonEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class CacheSideEffectException extends RuntimeException {
        private CacheSideEffectException(String message) {
            super(message);
        }
    }
}
