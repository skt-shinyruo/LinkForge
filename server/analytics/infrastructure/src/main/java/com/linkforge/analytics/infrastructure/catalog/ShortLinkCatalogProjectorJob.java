package com.linkforge.analytics.infrastructure.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class ShortLinkCatalogProjectorJob {

    private static final Logger log = LoggerFactory.getLogger(ShortLinkCatalogProjectorJob.class);

    static final String CONSUMER = "analytics-link-catalog-projector";
    static final int BATCH_LIMIT = 200;
    private static final String PRODUCER = "shortlink";

    private final IntegrationEventStore store;
    private final IntegrationCheckpointRepository checkpoints;
    private final IntegrationDeadLetterRepository deadLetter;
    private final AnalyticsLinkCatalogMapper catalogMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public ShortLinkCatalogProjectorJob(
            IntegrationEventStore store,
            IntegrationCheckpointRepository checkpoints,
            IntegrationDeadLetterRepository deadLetter,
            AnalyticsLinkCatalogMapper catalogMapper,
            ObjectMapper objectMapper,
            PlatformTransactionManager txManager
    ) {
        this.store = store;
        this.checkpoints = checkpoints;
        this.deadLetter = deadLetter;
        this.catalogMapper = catalogMapper;
        this.objectMapper = objectMapper;
        TransactionTemplate tpl = new TransactionTemplate(txManager);
        tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tx = tpl;
    }

    @Scheduled(fixedDelayString = "${APP_ANALYTICS_SHORTLINK_CATALOG_PROJECTOR_DELAY_MS:2000}")
    @SchedulerLock(name = "lf:job:analytics:shortlink-catalog-projector", lockAtMostFor = "PT2M")
    public void project() {
        int total = 0;
        for (int i = 0; i < 10; i++) {
            int n = projectOnce();
            total += n;
            if (n <= 0) {
                break;
            }
        }
        if (total > 0) {
            log.info("project shortlink catalog done: processed={}", total);
        }
    }

    int projectOnce() {
        long lastSeq = checkpoints.loadOrInit(CONSUMER);
        List<IntegrationEventRow> events = store.listAfterSeq(lastSeq, BATCH_LIMIT);
        if (events == null || events.isEmpty()) {
            return 0;
        }

        int processed = 0;
        for (IntegrationEventRow e : events) {
            if (e == null) {
                continue;
            }
            try {
                tx.executeWithoutResult(status -> {
                    try {
                        apply(e);
                        checkpoints.update(CONSUMER, e.seq());
                    } catch (DataAccessException ex) {
                        // Transient: do NOT advance checkpoint; stop processing so it retries next run.
                        throw ex;
                    } catch (Exception ex) {
                        // Poison isolation: record the failure and advance checkpoint so the consumer does not get stuck.
                        deadLetter.upsertFailure(CONSUMER, e, 1, truncate(ex.getMessage(), 512));
                        checkpoints.update(CONSUMER, e.seq());
                    }
                });
                processed++;
            } catch (DataAccessException ex) {
                log.debug(
                        "analytics catalog projector transient failure: consumer={}, seq={}, eventId={}, eventType={}, err={}",
                        CONSUMER,
                        e.seq(),
                        e.eventId(),
                        e.eventType(),
                        ex.getMessage()
                );
                return processed;
            }
        }
        return processed;
    }

    private void apply(IntegrationEventRow row) throws Exception {
        if (row == null) {
            return;
        }
        if (!PRODUCER.equals(row.producer())) {
            return;
        }
        String eventType = row.eventType();
        if (eventType == null || eventType.isBlank()) {
            return;
        }

        switch (eventType) {
            case ShortLinkEventTypes.SHORT_LINK_CREATED_V1 -> upsert(read(row.payloadJson(), ShortLinkCreatedV1.class).snapshot());
            case ShortLinkEventTypes.SHORT_LINK_UPDATED_V1 -> upsert(read(row.payloadJson(), ShortLinkUpdatedV1.class).snapshot());
            case ShortLinkEventTypes.SHORT_LINK_ARCHIVED_V1 -> upsert(read(row.payloadJson(), ShortLinkArchivedV1.class).snapshot());
            case ShortLinkEventTypes.SHORT_LINK_RESTORED_V1 -> upsert(read(row.payloadJson(), ShortLinkRestoredV1.class).snapshot());
            case ShortLinkEventTypes.SHORT_LINK_DELETED_V1 -> markDeleted(read(row.payloadJson(), ShortLinkDeletedV1.class).snapshot());
            default -> {
                // ignore unknown type
            }
        }
    }

    private void upsert(ShortLinkPublicSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("shortlink snapshot is null");
        }
        if (snapshot.code() == null || snapshot.code().isBlank()) {
            throw new IllegalArgumentException("shortlink snapshot/code is blank");
        }
        if (snapshot.originalUrl() == null || snapshot.originalUrl().isBlank()) {
            throw new IllegalArgumentException("shortlink snapshot/originalUrl is blank");
        }

        AnalyticsLinkCatalogRow row = new AnalyticsLinkCatalogRow();
        row.setTenantId(snapshot.tenantId());
        row.setLinkId(snapshot.linkId());
        row.setCode(snapshot.code());
        row.setOriginalUrl(snapshot.originalUrl());
        row.setArchivedAt(toUtcLocalDateTime(snapshot.archivedAtUtc()));
        row.setDeleted(Boolean.FALSE);
        catalogMapper.upsert(row);
    }

    private void markDeleted(ShortLinkPublicSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("shortlink snapshot is null");
        }

        AnalyticsLinkCatalogRow row = new AnalyticsLinkCatalogRow();
        row.setTenantId(snapshot.tenantId());
        row.setLinkId(snapshot.linkId());
        row.setCode(null);
        row.setOriginalUrl(null);
        row.setArchivedAt(null);
        row.setDeleted(Boolean.TRUE);
        catalogMapper.upsert(row);
    }

    private <T> T read(String payloadJson, Class<T> type) throws Exception {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("integration event payload_json is blank");
        }
        return objectMapper.readValue(payloadJson, type);
    }

    private static LocalDateTime toUtcLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String truncate(String v, int maxLen) {
        if (v == null) {
            return null;
        }
        if (maxLen <= 0 || v.length() <= maxLen) {
            return v;
        }
        return v.substring(0, maxLen);
    }
}
