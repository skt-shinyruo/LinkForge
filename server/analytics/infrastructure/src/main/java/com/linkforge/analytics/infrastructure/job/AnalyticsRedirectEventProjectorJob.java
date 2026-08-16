package com.linkforge.analytics.infrastructure.job;

import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.observability.OperationalMetrics;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将访问 Redis Stream 投影为实时 Redis 聚合的消费者。
 *
 * <p>消费顺序是先读取本 consumer 的 pending，再读取新消息；每条消息只有在
 * {@link AnalyticsRedisAggregateWriter#write(Map)} 返回后才会 ACK。因 Redis 写入与 ACK 不在同一
 * 原子事务中，事件交付仍是至少一次；标准事件由 writer 使用 requestId 幂等投影，因此 ACK 重放不会重复 PV。</p>
 *
 * <p>不含 {@code visitorKey} 的历史或不完整消息没有足够信息计算 UV，会被直接 ACK 丢弃；这是一项
 * 明确的数据兼容策略，而不是重试条件。</p>
 */
@Component
public class AnalyticsRedirectEventProjectorJob {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRedirectEventProjectorJob.class);

    private static final String GROUP = "lf-visit-projector";
    private static final String CONSUMER = "lf-visit-projector-consumer";
    private static final int BATCH_SIZE = 200;

    private final AnalyticsProperties analyticsProperties;
    private final AnalyticsRedisAggregateWriter aggregateWriter;
    private final RedisStreamBatchConsumer streamConsumer;
    private final OperationalMetrics metrics;

    public AnalyticsRedirectEventProjectorJob(
            StringRedisTemplate redis,
            AnalyticsProperties analyticsProperties,
            AnalyticsRedisAggregateWriter aggregateWriter
    ) {
        this(redis, analyticsProperties, aggregateWriter, OperationalMetrics.noop());
    }

    @Autowired
    public AnalyticsRedirectEventProjectorJob(
            StringRedisTemplate redis,
            AnalyticsProperties analyticsProperties,
            AnalyticsRedisAggregateWriter aggregateWriter,
            OperationalMetrics metrics
    ) {
        this.analyticsProperties = analyticsProperties;
        this.aggregateWriter = aggregateWriter;
        this.metrics = metrics == null ? OperationalMetrics.noop() : metrics;
        this.streamConsumer = new RedisStreamBatchConsumer(redis, GROUP, CONSUMER, "analytics_visit_projector", this.metrics);
    }

    /**
     * 排空可消费消息直到当前批次为空或出现可重试失败。
     *
     * <p>分布式锁避免多个实例同时跑同一调度轮次，但 Redis consumer group 仍是最终的交付协调机制。</p>
     */
    @Scheduled(fixedDelayString = "${APP_ANALYTICS_REDIRECT_EVENT_PROJECTOR_DELAY_MS:2000}")
    @SchedulerLock(name = "lf:job:analytics:redirect-event-projector", lockAtMostFor = "PT2M")
    public void project() {
        String streamKey = AnalyticsKeys.visitEventStreamKey();
        if (!streamConsumer.ensureGroup(streamKey)) {
            return;
        }

        while (true) {
            AnalyticsProperties.Events events = analyticsProperties == null ? null : analyticsProperties.getEvents();
            boolean reclaimEnabled = events == null || events.isPendingReclaimEnabled();
            long minIdleMs = events == null ? 60_000L : Math.max(events.getPendingReclaimMinIdleMs(), 0L);
            int reclaimCount = events == null ? BATCH_SIZE : Math.max(events.getPendingReclaimCount(), 1);
            List<MapRecord<String, Object, Object>> records = streamConsumer.readNext(
                    streamKey,
                    BATCH_SIZE,
                    null,
                    reclaimEnabled,
                    Duration.ofMillis(minIdleMs),
                    reclaimCount
            );
            if (records == null || records.isEmpty()) {
                return;
            }
            if (!projectRecords(streamKey, records)) {
                return;
            }
        }
    }

    /**
     * 投影一批消息，并仅确认已成功投影或明确不可投影的记录。
     *
     * @return {@code false} 表示聚合写入失败；尚未 ACK 的记录留在 pending 中等待重试
     */
    boolean projectRecords(String streamKey, List<MapRecord<String, Object, Object>> records) {
        if (records == null || records.isEmpty()) {
            return true;
        }

        List<RecordId> ackIds = new ArrayList<>(records.size());
        for (MapRecord<String, Object, Object> record : records) {
            if (record == null || record.getId() == null || record.getValue() == null) {
                continue;
            }

            Map<String, String> values = normalize(record.getValue());
            if (!isProjectable(values)) {
                ackIds.add(record.getId());
                continue;
            }

            try {
                aggregateWriter.write(values);
                metrics.increment("linkforge.job.events", "job", "analytics_visit_projector", "result", "success");
                ackIds.add(record.getId());
            } catch (Exception e) {
                metrics.increment("linkforge.job.failures", "job", "analytics_visit_projector", "stage", "redis_projection");
                log.warn("project analytics visit event failed: streamId={}, err={}", record.getId(), e.getMessage());
                acknowledgeAll(streamKey, ackIds);
                return false;
            }
        }

        return acknowledgeAll(streamKey, ackIds);
    }

    private boolean acknowledgeAll(String streamKey, List<RecordId> ids) {
        return ids == null || ids.isEmpty() || streamConsumer.acknowledge(streamKey, ids) == ids.size();
    }

    private static Map<String, String> normalize(Map<Object, Object> raw) {
        Map<String, String> values = new HashMap<>(raw.size());
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            values.put(String.valueOf(entry.getKey()), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
        return values;
    }

    private static boolean isProjectable(Map<String, String> values) {
        return trimToNull(values.get("visitorKey")) != null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
