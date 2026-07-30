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

/**
 * 将 ShortLink integration outbox 投影为 Analytics 链接目录的消费者。
 *
 * <p>目录为应用/域范围报表和额度 SQL 提供链接归属与展示字段。每个事件在独立事务中先应用目录变更、
 * 再推进 checkpoint；数据库错误回滚该事务并停在当前 seq，之后重试。未知事件类型可安全跳过，以兼容
 * 新生产者。</p>
 *
 * <p>无法解析或违反快照基本约束的事件会写 integration DLQ 并推进 checkpoint，避免坏历史事件永久
 * 阻塞消费者。DLQ 的可靠性受其底层实现约束，不能将这条异步投影理解为 exactly-once 的跨上下文事务。</p>
 */
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

    /**
     * 在单次调度中最多连续处理十批，防止积压追赶长期占用调度线程。
     */
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

    /**
     * 处理 checkpoint 之后的一批 ShortLink 事件。
     *
     * @return 成功完成事务或被明确跳过的事件数；数据库失败时返回已完成数量并留下当前 checkpoint
     */
    int projectOnce() {
        long lastSeq = checkpoints.loadOrInit(CONSUMER);
        List<IntegrationEventRow> events = store.listAfterSeqByProducer(PRODUCER, lastSeq, BATCH_LIMIT);
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
                        throw ex;
                    } catch (Exception ex) {
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
        if (row == null || !PRODUCER.equals(row.producer())) {
            return;
        }
        String eventType = row.eventType();
        if (eventType == null || eventType.isBlank()) {
            return;
        }

        switch (eventType) {
            case ShortLinkEventTypes.SHORT_LINK_CREATED_V1 -> upsert(read(row.payloadJson(), ShortLinkCreatedV1.class).snapshot(), false);
            case ShortLinkEventTypes.SHORT_LINK_UPDATED_V1 -> upsert(read(row.payloadJson(), ShortLinkUpdatedV1.class).snapshot(), false);
            case ShortLinkEventTypes.SHORT_LINK_ARCHIVED_V1 -> upsert(read(row.payloadJson(), ShortLinkArchivedV1.class).snapshot(), false);
            case ShortLinkEventTypes.SHORT_LINK_RESTORED_V1 -> upsert(read(row.payloadJson(), ShortLinkRestoredV1.class).snapshot(), false);
            case ShortLinkEventTypes.SHORT_LINK_DELETED_V1 -> upsert(read(row.payloadJson(), ShortLinkDeletedV1.class).snapshot(), true);
            default -> {
                // Unknown shortlink events are ignored so newer producers do not block this consumer.
            }
        }
    }

    private void upsert(ShortLinkPublicSnapshot snapshot, boolean deleted) {
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
        row.setApplicationId(snapshot.applicationId());
        row.setDomainId(snapshot.domainId());
        row.setCode(snapshot.code());
        row.setOriginalUrl(snapshot.originalUrl());
        row.setArchivedAt(toUtcLocalDateTime(snapshot.archivedAtUtc()));
        row.setDeleted(deleted);
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
