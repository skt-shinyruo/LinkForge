package com.linkforge.analytics.infrastructure.job;

import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkVisitEventMapper;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.config.IdProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    private final StringRedisTemplate redis;
    private final LinkVisitEventMapper visitEventMapper;
    private final AnalyticsProperties analyticsProperties;
    private final String consumerName;
    private final VisitEventBatchAssembler batchAssembler;
    private final VisitEventDeadLetterWriter deadLetterWriter;

    public AnalyticsEventIngestJob(
            StringRedisTemplate redis,
            LinkVisitEventMapper visitEventMapper,
            AnalyticsProperties analyticsProperties,
            IdProperties idProperties,
            SnowflakeIdGenerator idGenerator
    ) {
        this.redis = redis;
        this.visitEventMapper = visitEventMapper;
        this.analyticsProperties = analyticsProperties;
        this.consumerName = resolveConsumerName(analyticsProperties, idProperties);
        this.batchAssembler = new VisitEventBatchAssembler(idGenerator);
        this.deadLetterWriter = new VisitEventDeadLetterWriter(redis);
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
        if (!ensureGroup(streamKey)) {
            return;
        }

        Consumer consumer = Consumer.from(GROUP, consumerName);

        // 1) 优先处理本 consumer 的 pending（例如重启后继续消费）
        StreamReadOptions drainOptions = StreamReadOptions.empty().count(200);
        List<MapRecord<String, Object, Object>> records = readSafe(
                consumer,
                drainOptions,
                StreamOffset.create(streamKey, ReadOffset.from("0-0"))
        );
        if (records != null && !records.isEmpty()) {
            ingestRecords(streamKey, records);
            return;
        }

        // 2) 定期接管“已闲置”的 pending（避免 consumer 漂移/下线导致卡死）
        if (cfg.isPendingReclaimEnabled()) {
            Duration minIdle = Duration.ofMillis(Math.max(cfg.getPendingReclaimMinIdleMs(), 0));
            int count = Math.max(cfg.getPendingReclaimCount(), 1);
            List<MapRecord<String, Object, Object>> claimed = reclaimPending(streamKey, consumer, minIdle, count);
            if (claimed != null && !claimed.isEmpty()) {
                ingestRecords(streamKey, claimed);
                return;
            }
        }

        // 3) 读取新消息
        StreamReadOptions options = StreamReadOptions.empty().count(200).block(Duration.ofMillis(200));

        records = readSafe(consumer, options, StreamOffset.create(streamKey, ReadOffset.lastConsumed()));

        if (records == null || records.isEmpty()) {
            return;
        }

        ingestRecords(streamKey, records);
    }

    private List<MapRecord<String, Object, Object>> readSafe(
            Consumer consumer,
            StreamReadOptions options,
            StreamOffset<String> offset
    ) {
        try {
            return redis.opsForStream().read(consumer, options, offset);
        } catch (Exception e) {
            log.debug("read visit stream failed: streamKey={}, err={}", offset == null ? null : offset.getKey(), e.getMessage());
            return null;
        }
    }

    private List<MapRecord<String, Object, Object>> reclaimPending(
            String streamKey,
            Consumer consumer,
            Duration minIdleTime,
            int count
    ) {
        PendingMessages pending;
        try {
            pending = redis.opsForStream().pending(streamKey, GROUP, Range.unbounded(), count);
        } catch (Exception e) {
            log.debug("pending visit stream failed: streamKey={}, err={}", streamKey, e.getMessage());
            return null;
        }
        if (pending == null || pending.isEmpty()) {
            return null;
        }

        List<RecordId> ids = new ArrayList<>(Math.min(pending.size(), count));
        for (PendingMessage p : pending) {
            if (p == null || p.getId() == null) {
                continue;
            }
            if (consumer != null && consumer.getName() != null && consumer.getName().equals(p.getConsumerName())) {
                continue;
            }
            Duration idle = p.getElapsedTimeSinceLastDelivery();
            if (idle != null && idle.compareTo(minIdleTime) < 0) {
                continue;
            }
            ids.add(p.getId());
            if (ids.size() >= count) {
                break;
            }
        }
        if (ids.isEmpty()) {
            return null;
        }

        try {
            return redis.opsForStream().claim(streamKey, GROUP, consumer.getName(), minIdleTime, ids.toArray(new RecordId[0]));
        } catch (Exception e) {
            log.debug("claim pending visit stream failed: streamKey={}, size={}, err={}", streamKey, ids.size(), e.getMessage());
            return null;
        }
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
            acknowledge(streamKey, ackAlways);
            return;
        }

        try {
            visitEventMapper.batchInsertIgnore(items.stream().map(VisitEventBatchAssembler.IngestItem::row).toList());
        } catch (DataIntegrityViolationException e) {
            log.warn("ingest visit events failed (data integrity): size={}, err={}", items.size(), e.getMessage());
            acknowledge(streamKey, ackAlways);
            isolatePoisonAndAck(streamKey, items);
            return;
        } catch (DataAccessException e) {
            log.warn("ingest visit events failed: size={}, err={}", items.size(), e.getMessage());
            acknowledge(streamKey, ackAlways);
            return;
        }

        if (!ackAlways.isEmpty()) {
            acknowledge(streamKey, ackAlways);
        }
        acknowledge(streamKey, items.stream().map(VisitEventBatchAssembler.IngestItem::recordId).toList());
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
                // Likely DB transient/fatal issue: keep pending for retry and avoid tight per-row loop.
                log.debug("ingest visit event row failed: streamId={}, err={}", item.recordId(), e.getMessage());
                break;
            }
        }

        if (!ackIds.isEmpty()) {
            acknowledge(streamKey, ackIds);
        }
    }

    private void acknowledge(String streamKey, List<RecordId> ackIds) {
        if (ackIds == null || ackIds.isEmpty()) {
            return;
        }
        try {
            redis.opsForStream().acknowledge(streamKey, GROUP, ackIds.toArray(new RecordId[0]));
        } catch (Exception e) {
            log.debug("ack visit stream failed: size={}, err={}", ackIds.size(), e.getMessage());
        }
    }

    private boolean ensureGroup(String streamKey) {
        try {
            Boolean exists = redis.hasKey(streamKey);
            if (exists == null || !exists) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }

        try {
            redis.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), GROUP);
            return true;
        } catch (Exception e) {
            if (RedisStreamGroupErrors.isBusyGroup(e)) {
                return true;
            }
            String msg = e.getMessage();
            // key 不存在或 Redis 不支持 stream 等场景：跳过本轮
            log.debug("create group failed: streamKey={}, group={}, err={}", streamKey, GROUP, msg);
            return false;
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
