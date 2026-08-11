package com.linkforge.analytics.infrastructure.job;

import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkVisitEventMapper;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.config.IdProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.observability.OperationalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * 将访问 Redis Stream 中的可采样明细异步写入 MySQL 的消费者。
 *
 * <p>访问流也供核心 PV/UV 投影使用，但本作业只在 {@code analytics.events.enabled} 时运行，并按
 * {@code sampleRate} 决定是否保留明细。因此关闭明细或采样丢弃不影响核心统计。读取顺序为本 consumer
 * 的 pending、可接管的闲置 pending、再到新消息。</p>
 *
 * <p>批量插入以 {@code requestId} 的数据库唯一性配合 {@code INSERT ... ON DUPLICATE KEY} 实现
 * 幂等写入；ACK 与数据库提交不在同一事务中，ACK 失败会导致安全的重放。普通数据库失败保留 pending；
 * 数据完整性失败时逐条隔离，坏记录写入 DLQ 后 ACK。DLQ 写入是 best-effort，故 DLQ 故障不会阻塞
 * 原消息，也可能导致诊断记录缺失。</p>
 */
@Component
public class AnalyticsEventIngestJob {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventIngestJob.class);

    private static final String GROUP = "lf-visit-ingest";
    private static final Pattern NON_SAFE = Pattern.compile("[^a-zA-Z0-9._:-]");

    private final LinkVisitEventMapper visitEventMapper;
    private final AnalyticsProperties analyticsProperties;
    private final VisitEventBatchAssembler batchAssembler;
    private final VisitEventDeadLetterWriter deadLetterWriter;
    private final RedisStreamBatchConsumer streamConsumer;
    private final OperationalMetrics metrics;

    public AnalyticsEventIngestJob(
            StringRedisTemplate redis,
            LinkVisitEventMapper visitEventMapper,
            AnalyticsProperties analyticsProperties,
            IdProperties idProperties,
            SnowflakeIdGenerator idGenerator
    ) {
        this(redis, visitEventMapper, analyticsProperties, idProperties, idGenerator, OperationalMetrics.noop());
    }

    @Autowired
    public AnalyticsEventIngestJob(
            StringRedisTemplate redis,
            LinkVisitEventMapper visitEventMapper,
            AnalyticsProperties analyticsProperties,
            IdProperties idProperties,
            SnowflakeIdGenerator idGenerator,
            OperationalMetrics metrics
    ) {
        this.visitEventMapper = visitEventMapper;
        this.analyticsProperties = analyticsProperties;
        this.metrics = metrics == null ? OperationalMetrics.noop() : metrics;
        String consumerName = resolveConsumerName(analyticsProperties, idProperties);
        this.batchAssembler = new VisitEventBatchAssembler(idGenerator);
        this.deadLetterWriter = new VisitEventDeadLetterWriter(redis, this.metrics);
        this.streamConsumer = new RedisStreamBatchConsumer(redis, GROUP, consumerName, "analytics_visit_ingest", this.metrics);
    }

    /**
     * 执行一轮明细消费，不把 Redis 或数据库故障传播到调度线程。
     *
     * <p>即使访问流持续有消息，关闭明细功能也只停止本消费者；聚合投影消费者仍可处理同一 Stream。</p>
     */
    @Scheduled(fixedDelayString = "${APP_ANALYTICS_EVENT_INGEST_DELAY_MS:2000}")
    public void ingest() {
        AnalyticsProperties.Events cfg = analyticsProperties == null ? null : analyticsProperties.getEvents();
        if (cfg == null || !cfg.isEnabled()) {
            return;
        }

        String streamKey = AnalyticsKeys.visitEventStreamKey();
        if (!streamConsumer.ensureGroup(streamKey)) {
            return;
        }

        List<MapRecord<String, Object, Object>> records = streamConsumer.readNext(
                streamKey,
                200,
                Duration.ofMillis(200),
                cfg.isPendingReclaimEnabled(),
                Duration.ofMillis(Math.max(cfg.getPendingReclaimMinIdleMs(), 0L)),
                Math.max(cfg.getPendingReclaimCount(), 1)
        );

        if (records == null || records.isEmpty()) {
            return;
        }

        ingestRecords(streamKey, records);
    }

    /**
     * 组装、采样并写入一批记录。
     *
     * <p>结构非法、缺失 requestId 或被采样丢弃的记录会 ACK，不会因为不可持久化数据永久占用 pending。
     * 可恢复的数据访问错误不 ACK 有效项，以便下一轮重试。</p>
     */
    void ingestRecords(String streamKey, List<MapRecord<String, Object, Object>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        VisitEventBatchAssembler.Batch batch = applyDetailSampling(batchAssembler.assemble(records));
        List<VisitEventBatchAssembler.IngestItem> items = batch.items();
        List<RecordId> ackAlways = batch.ackAlways();

        if (items.isEmpty()) {
            streamConsumer.acknowledge(streamKey, ackAlways);
            return;
        }

        long startedAt = System.nanoTime();
        try {
            visitEventMapper.batchInsertIgnore(items.stream().map(VisitEventBatchAssembler.IngestItem::row).toList());
            metrics.add("linkforge.job.rows", items.size(), "job", "analytics_visit_ingest", "result", "success");
            metrics.record("linkforge.job.db_batch", Duration.ofNanos(System.nanoTime() - startedAt), "job", "analytics_visit_ingest", "result", "success");
        } catch (DataIntegrityViolationException e) {
            metrics.increment("linkforge.job.failures", "job", "analytics_visit_ingest", "stage", "data_integrity");
            metrics.record("linkforge.job.db_batch", Duration.ofNanos(System.nanoTime() - startedAt), "job", "analytics_visit_ingest", "result", "failure");
            log.warn("ingest visit events failed (data integrity): size={}, err={}", items.size(), e.getMessage());
            streamConsumer.acknowledge(streamKey, ackAlways);
            isolatePoisonAndAck(streamKey, items);
            return;
        } catch (DataAccessException e) {
            metrics.increment("linkforge.job.failures", "job", "analytics_visit_ingest", "stage", "database");
            metrics.record("linkforge.job.db_batch", Duration.ofNanos(System.nanoTime() - startedAt), "job", "analytics_visit_ingest", "result", "failure");
            log.warn("ingest visit events failed: size={}, err={}", items.size(), e.getMessage());
            streamConsumer.acknowledge(streamKey, ackAlways);
            return;
        }

        if (!ackAlways.isEmpty()) {
            streamConsumer.acknowledge(streamKey, ackAlways);
        }
        streamConsumer.acknowledge(streamKey, items.stream().map(VisitEventBatchAssembler.IngestItem::recordId).toList());
    }

    private VisitEventBatchAssembler.Batch applyDetailSampling(VisitEventBatchAssembler.Batch batch) {
        if (batch == null) {
            return new VisitEventBatchAssembler.Batch(List.of(), List.of());
        }

        AnalyticsProperties.Events cfg = analyticsProperties == null ? null : analyticsProperties.getEvents();
        List<RecordId> ackAlways = new ArrayList<>(batch.ackAlways());
        List<VisitEventBatchAssembler.IngestItem> sampledItems = new ArrayList<>(batch.items().size());

        for (VisitEventBatchAssembler.IngestItem item : batch.items()) {
            if (item == null || item.recordId() == null) {
                continue;
            }
            if (shouldPersistDetail(cfg)) {
                sampledItems.add(item);
            } else {
                ackAlways.add(item.recordId());
            }
        }

        return new VisitEventBatchAssembler.Batch(List.copyOf(sampledItems), List.copyOf(ackAlways));
    }

    private static boolean shouldPersistDetail(AnalyticsProperties.Events cfg) {
        if (cfg == null || !cfg.isEnabled()) {
            return false;
        }
        double sampleRate = cfg.getSampleRate();
        if (Double.isNaN(sampleRate) || sampleRate <= 0) {
            return false;
        }
        if (sampleRate >= 1) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() < sampleRate;
    }

    /**
     * 批量完整性错误后的逐条隔离。
     *
     * <p>单条完整性错误视为 poison record：尽力写 DLQ 后确认原消息，防止整个组永久卡住。普通数据访问
     * 错误被视为暂态或基础设施故障，停止隔离并保留余下消息 pending。</p>
     */
    private void isolatePoisonAndAck(String streamKey, List<VisitEventBatchAssembler.IngestItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<RecordId> ackIds = new ArrayList<>(items.size());
        for (VisitEventBatchAssembler.IngestItem item : items) {
            if (item == null || item.recordId() == null || item.row() == null) {
                continue;
            }
            try {
                // Fallback to per-row insert: isolate the poison record so the stream does not get stuck pending.
                visitEventMapper.batchInsertIgnore(List.of(item.row()));
                ackIds.add(item.recordId());
            } catch (DataIntegrityViolationException e) {
                deadLetterWriter.write(streamKey, item.recordId(), item.row(), e);
                ackIds.add(item.recordId());
            } catch (DataAccessException e) {
                metrics.increment("linkforge.job.failures", "job", "analytics_visit_ingest", "stage", "poison_isolation");
                // Likely DB transient/fatal issue: keep pending for retry and avoid tight per-row loop.
                log.debug("ingest visit event row failed: streamId={}, err={}", item.recordId(), e.getMessage());
                break;
            }
        }

        if (!ackIds.isEmpty()) {
            streamConsumer.acknowledge(streamKey, ackIds);
        }
    }

    private static String resolveConsumerName(AnalyticsProperties analyticsProperties, IdProperties idProperties) {
        AnalyticsProperties.Events cfg = analyticsProperties == null ? null : analyticsProperties.getEvents();
        String configured = cfg == null ? null : trimToNull(cfg.getConsumerName());
        if (configured != null) {
            return NON_SAFE.matcher(configured).replaceAll("_");
        }

        long workerId = idProperties == null ? 0 : idProperties.getWorkerId();
        long datacenterId = idProperties == null ? 0 : idProperties.getDatacenterId();

        String host = trimToNull(System.getenv("HOSTNAME"));
        if (host == null) {
            host = "unknown";
        }

        String derived = "c-" + host + "-" + workerId + "-" + datacenterId;
        return NON_SAFE.matcher(derived).replaceAll("_");
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isBlank() ? null : t;
    }
}
