package com.linkforge.analytics.infrastructure.job;

import com.linkforge.foundation.observability.OperationalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.Limit;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Analytics 内部统一的 Redis Stream consumer-group 交付适配器。
 *
 * <p>每次读取严格按“当前 consumer pending、超时的其他 consumer pending、新消息”排序。业务处理器只在
 * 持久化成功或明确丢弃坏消息后 ACK，从而避免各个调度作业各自实现并逐渐产生不同的重试语义。</p>
 *
 * <p>健康状态以 consumer group 的 {@code lag + pending} 为剩余工作量：首个非零或持平/下降为
 * {@code draining}，连续增长为 {@code backlog}，零为 {@code no_traffic}；pending 或 XINFO 不可用时为
 * {@code degraded}，且不会伪装成零流量。所有指标标签只包含固定 operation、stage、source 和 state。</p>
 */
final class RedisStreamBatchConsumer {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamBatchConsumer.class);

    private final StringRedisTemplate redis;
    private final String group;
    private final Consumer consumer;
    private final String operation;
    private final OperationalMetrics metrics;
    private final boolean observeOnRead;
    private Long previousRemaining;

    RedisStreamBatchConsumer(StringRedisTemplate redis, String group, String consumerName, String operation) {
        this(redis, group, consumerName, operation, OperationalMetrics.noop());
    }

    RedisStreamBatchConsumer(
            StringRedisTemplate redis,
            String group,
            String consumerName,
            String operation,
            OperationalMetrics metrics
    ) {
        this(redis, group, consumerName, operation, metrics, true);
    }

    RedisStreamBatchConsumer(
            StringRedisTemplate redis,
            String group,
            String consumerName,
            String operation,
            OperationalMetrics metrics,
            boolean observeOnRead
    ) {
        this.redis = redis;
        this.group = group;
        this.consumer = Consumer.from(group, consumerName);
        this.operation = operation;
        this.metrics = metrics == null ? OperationalMetrics.noop() : metrics;
        this.observeOnRead = observeOnRead;
    }

    boolean ensureGroup(String streamKey) {
        try {
            Boolean exists = redis.hasKey(streamKey);
            if (exists == null || !exists) {
                return false;
            }
        } catch (Exception e) {
            metrics.increment("linkforge.stream.failures", "operation", operation, "stage", "exists");
            log.debug("{} stream existence check failed: streamKey={}, err={}", operation, streamKey, e.getMessage());
            return false;
        }

        try {
            redis.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), group);
            return true;
        } catch (Exception e) {
            if (RedisStreamGroupErrors.isBusyGroup(e)) {
                return true;
            }
            metrics.increment("linkforge.stream.failures", "operation", operation, "stage", "create_group");
            log.debug("{} stream group creation failed: streamKey={}, group={}, err={}",
                    operation, streamKey, group, e.getMessage());
            return false;
        }
    }

    List<MapRecord<String, Object, Object>> readNext(
            String streamKey,
            int batchSize,
            Duration block,
            boolean reclaimEnabled,
            Duration reclaimMinIdle,
            int reclaimCount
    ) {
        if (observeOnRead) {
            publishStreamState(inspectStreamState(streamKey));
        }
        StreamReadOptions pendingOptions = StreamReadOptions.empty().count(batchSize);
        List<MapRecord<String, Object, Object>> records = readSafe(
                pendingOptions,
                StreamOffset.create(streamKey, ReadOffset.from("0-0"))
        );
        if (!isEmpty(records)) {
            reportDelivery(records.size(), "own_pending");
            metrics.add("linkforge.stream.retries", records.size(), "operation", operation, "source", "own_pending");
            return records;
        }

        if (reclaimEnabled) {
            records = reclaimPending(streamKey, reclaimMinIdle, reclaimCount);
            if (!isEmpty(records)) {
                reportDelivery(records.size(), "reclaimed");
                metrics.add("linkforge.stream.retries", records.size(), "operation", operation, "source", "reclaimed");
                return records;
            }
        }

        StreamReadOptions newOptions = StreamReadOptions.empty().count(batchSize);
        if (block != null && !block.isNegative() && !block.isZero()) {
            newOptions = newOptions.block(block);
        }
        records = readSafe(newOptions, StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
        if (!isEmpty(records)) {
            reportDelivery(records.size(), "new");
        }
        return records;
    }

    /**
     * ACK 一批记录并返回 Redis 确认的真实数量。
     *
     * <p>返回 {@code 0} 表示没有记录被确认或 ACK 观测失败。调用方不得把请求数量当作成功数量，
     * 否则 Redis 故障或部分 ACK 会制造虚假的排空进度。</p>
     */
    long acknowledge(String streamKey, List<RecordId> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0L;
        }
        try {
            Long acknowledged = redis.opsForStream().acknowledge(streamKey, group, ids.toArray(new RecordId[0]));
            if (acknowledged == null) {
                metrics.increment("linkforge.stream.failures", "operation", operation, "stage", "ack_result");
                return 0L;
            }
            long actual = Math.min(Math.max(acknowledged, 0L), ids.size());
            metrics.add(
                    "linkforge.stream.acknowledged",
                    actual,
                    "operation",
                    operation
            );
            if (actual != ids.size()) {
                metrics.increment("linkforge.stream.failures", "operation", operation, "stage", "partial_ack");
            }
            return actual;
        } catch (Exception e) {
            metrics.increment("linkforge.stream.failures", "operation", operation, "stage", "ack");
            log.debug("{} stream ACK failed: streamKey={}, size={}, err={}",
                    operation, streamKey, ids.size(), e.getMessage());
            return 0L;
        }
    }

    private List<MapRecord<String, Object, Object>> readSafe(
            StreamReadOptions options,
            StreamOffset<String> offset
    ) {
        try {
            return redis.opsForStream().read(consumer, options, offset);
        } catch (Exception e) {
            metrics.increment("linkforge.stream.failures", "operation", operation, "stage", "read");
            log.debug("{} stream read failed: streamKey={}, err={}", operation, offset.getKey(), e.getMessage());
            return null;
        }
    }

    private List<MapRecord<String, Object, Object>> reclaimPending(
            String streamKey,
            Duration minIdle,
            int count
    ) {
        int safeCount = Math.max(count, 1);
        Duration safeMinIdle = minIdle == null || minIdle.isNegative() ? Duration.ZERO : minIdle;
        PendingMessages pending;
        try {
            pending = redis.opsForStream().pending(streamKey, group, Range.unbounded(), safeCount);
        } catch (Exception e) {
            metrics.increment("linkforge.stream.failures", "operation", operation, "stage", "pending");
            log.debug("{} stream pending lookup failed: streamKey={}, err={}", operation, streamKey, e.getMessage());
            return null;
        }
        if (pending == null || pending.isEmpty()) {
            return null;
        }

        List<RecordId> ids = new ArrayList<>(Math.min(pending.size(), safeCount));
        for (PendingMessage message : pending) {
            if (message == null || message.getId() == null || consumer.getName().equals(message.getConsumerName())) {
                continue;
            }
            Duration idle = message.getElapsedTimeSinceLastDelivery();
            if (idle != null && idle.compareTo(safeMinIdle) < 0) {
                continue;
            }
            ids.add(message.getId());
            if (ids.size() >= safeCount) {
                break;
            }
        }
        if (ids.isEmpty()) {
            return null;
        }

        try {
            return redis.opsForStream().claim(
                    streamKey,
                    group,
                    consumer.getName(),
                    safeMinIdle,
                    ids.toArray(new RecordId[0])
            );
        } catch (Exception e) {
            metrics.increment("linkforge.stream.failures", "operation", operation, "stage", "claim");
            log.debug("{} stream claim failed: streamKey={}, size={}, err={}",
                    operation, streamKey, ids.size(), e.getMessage());
            return null;
        }
    }

    private static boolean isEmpty(List<?> records) {
        return records == null || records.isEmpty();
    }

    private void reportDelivery(int size, String source) {
        metrics.add("linkforge.stream.deliveries", size, "operation", operation, "source", source);
    }

    StreamState inspectStreamState(String streamKey) {
        try {
            PendingMessagesSummary summary = redis.opsForStream().pending(streamKey, group);
            if (summary == null) {
                return observationFailed(streamKey, "pending_summary");
            }
            long pending = Math.max(summary.getTotalPendingMessages(), 0L);

            PendingMessages sample = pending <= 0
                    ? null
                    : redis.opsForStream().pending(streamKey, group, Range.unbounded(), Math.min(pending, 500L));
            long oldestIdleMillis = 0L;
            long oldestUnprocessedAgeMillis = 0L;
            long nowMillis = System.currentTimeMillis();
            if (sample != null) {
                for (PendingMessage message : sample) {
                    Duration idle = message == null ? null : message.getElapsedTimeSinceLastDelivery();
                    if (idle != null) {
                        oldestIdleMillis = Math.max(oldestIdleMillis, idle.toMillis());
                        oldestUnprocessedAgeMillis = Math.max(oldestUnprocessedAgeMillis, idle.toMillis());
                    }
                    RecordId id = message == null ? null : message.getId();
                    Long timestamp = id == null ? null : id.getTimestamp();
                    if (timestamp != null && timestamp > 0) {
                        oldestUnprocessedAgeMillis = Math.max(
                                oldestUnprocessedAgeMillis,
                                Math.max(nowMillis - timestamp, 0L)
                        );
                    }
                }
            }
            GroupState groupState = readGroupState(streamKey);
            if (groupState == null) {
                return observationFailed(streamKey, "xinfo_group");
            }
            long lag = Math.max(groupState.lag(), 0L);
            if (lag > 0) {
                RecordId oldestUndelivered = readOldestUndeliveredId(streamKey, groupState.lastDeliveredId());
                Long timestamp = oldestUndelivered == null ? null : oldestUndelivered.getTimestamp();
                if (timestamp != null && timestamp > 0) {
                    oldestUnprocessedAgeMillis = Math.max(
                            oldestUnprocessedAgeMillis,
                            Math.max(nowMillis - timestamp, 0L)
                    );
                }
            }
            return StreamState.observed(lag, pending, oldestIdleMillis, oldestUnprocessedAgeMillis);
        } catch (Exception e) {
            metrics.increment("linkforge.stream.failures", "operation", operation, "stage", "observe");
            log.debug("{} stream metrics lookup failed: streamKey={}, err={}", operation, streamKey, e.getMessage());
            return StreamState.unknown();
        }
    }

    synchronized void publishStreamState(StreamState state) {
        if (state == null || !state.observed()) {
            previousRemaining = null;
            metrics.set("linkforge.stream.observation_available", 0L, "operation", operation);
            reportHealth("degraded");
            return;
        }

        long remaining = saturatingAdd(state.lag(), state.pending());
        metrics.set("linkforge.stream.observation_available", 1L, "operation", operation);
        metrics.set("linkforge.stream.pending", state.pending(), "operation", operation);
        metrics.set("linkforge.stream.lag", state.lag(), "operation", operation);
        metrics.set("linkforge.stream.remaining", remaining, "operation", operation);
        metrics.set("linkforge.stream.oldest_pending_idle_millis",
                state.oldestPendingIdleMillis(), "operation", operation);
        metrics.set("linkforge.stream.oldest_unprocessed_age_millis",
                state.oldestUnprocessedAgeMillis(), "operation", operation);

        String health = remaining == 0L
                ? "no_traffic"
                : previousRemaining != null && remaining > previousRemaining ? "backlog" : "draining";
        previousRemaining = remaining;
        reportHealth(health);
    }

    private StreamState observationFailed(String streamKey, String stage) {
        metrics.increment("linkforge.stream.failures", "operation", operation, "stage", stage);
        log.debug("{} stream state is unavailable: streamKey={}, stage={}", operation, streamKey, stage);
        return StreamState.unknown();
    }

    private GroupState readGroupState(String streamKey) {
        Object raw = redis.execute((RedisCallback<Object>) connection -> connection.execute(
                "XINFO",
                "GROUPS".getBytes(StandardCharsets.UTF_8),
                streamKey.getBytes(StandardCharsets.UTF_8)
        ));
        if (!(raw instanceof List<?> groups)) {
            return null;
        }
        for (Object groupRaw : groups) {
            if (!(groupRaw instanceof List<?> fields)) {
                continue;
            }
            String name = null;
            Long lag = null;
            String lastDeliveredId = null;
            for (int i = 0; i + 1 < fields.size(); i += 2) {
                String field = asText(fields.get(i));
                if ("name".equals(field)) {
                    name = asText(fields.get(i + 1));
                } else if ("lag".equals(field)) {
                    lag = asLong(fields.get(i + 1));
                } else if ("last-delivered-id".equals(field)) {
                    lastDeliveredId = asText(fields.get(i + 1));
                }
            }
            if (group.equals(name)) {
                return lag == null ? null : new GroupState(lag, lastDeliveredId);
            }
        }
        return null;
    }

    private RecordId readOldestUndeliveredId(String streamKey, String lastDeliveredId) {
        if (lastDeliveredId == null || lastDeliveredId.isBlank()) {
            return null;
        }
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().range(
                streamKey,
                Range.from(Range.Bound.exclusive(lastDeliveredId)).to(Range.Bound.unbounded()),
                Limit.limit().count(1)
        );
        if (records == null || records.isEmpty() || records.get(0) == null) {
            return null;
        }
        return records.get(0).getId();
    }

    private void reportHealth(String active) {
        for (String state : List.of("no_traffic", "draining", "backlog", "degraded")) {
            metrics.set("linkforge.job.health", state.equals(active) ? 1L : 0L,
                    "job", operation, "state", state);
        }
    }

    private static long saturatingAdd(long left, long right) {
        if (left >= Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static String asText(Object value) {
        return value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        try {
            return Long.parseLong(asText(value));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private record GroupState(long lag, String lastDeliveredId) {
    }

    record StreamState(
            boolean observed,
            long lag,
            long pending,
            long oldestPendingIdleMillis,
            long oldestUnprocessedAgeMillis
    ) {

        static StreamState observed(
                long lag,
                long pending,
                long oldestPendingIdleMillis,
                long oldestUnprocessedAgeMillis
        ) {
            return new StreamState(
                    true,
                    Math.max(lag, 0L),
                    Math.max(pending, 0L),
                    Math.max(oldestPendingIdleMillis, 0L),
                    Math.max(oldestUnprocessedAgeMillis, 0L)
            );
        }

        static StreamState unknown() {
            return new StreamState(false, 0L, 0L, 0L, 0L);
        }

        static StreamState combine(StreamState left, StreamState right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            if (!left.observed || !right.observed) {
                return unknown();
            }
            return observed(
                    saturatingAdd(left.lag, right.lag),
                    saturatingAdd(left.pending, right.pending),
                    Math.max(left.oldestPendingIdleMillis, right.oldestPendingIdleMillis),
                    Math.max(left.oldestUnprocessedAgeMillis, right.oldestUnprocessedAgeMillis)
            );
        }
    }
}
