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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Analytics 内部统一的 Redis Stream consumer-group 交付适配器。
 *
 * <p>每次读取严格按“当前 consumer pending、超时的其他 consumer pending、新消息”排序。业务处理器只在
 * 持久化成功或明确丢弃坏消息后 ACK，从而避免各个调度作业各自实现并逐渐产生不同的重试语义。</p>
 */
final class RedisStreamBatchConsumer {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamBatchConsumer.class);

    private final StringRedisTemplate redis;
    private final String group;
    private final Consumer consumer;
    private final String operation;
    private final OperationalMetrics metrics;

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
        this.redis = redis;
        this.group = group;
        this.consumer = Consumer.from(group, consumerName);
        this.operation = operation;
        this.metrics = metrics == null ? OperationalMetrics.noop() : metrics;
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
        observeStreamState(streamKey);
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

    void acknowledge(String streamKey, List<RecordId> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try {
            Long acknowledged = redis.opsForStream().acknowledge(streamKey, group, ids.toArray(new RecordId[0]));
            metrics.add(
                    "linkforge.stream.acknowledged",
                    acknowledged == null ? ids.size() : acknowledged,
                    "operation",
                    operation
            );
        } catch (Exception e) {
            metrics.increment("linkforge.stream.failures", "operation", operation, "stage", "ack");
            log.debug("{} stream ACK failed: streamKey={}, size={}, err={}",
                    operation, streamKey, ids.size(), e.getMessage());
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

    private void observeStreamState(String streamKey) {
        try {
            PendingMessagesSummary summary = redis.opsForStream().pending(streamKey, group);
            long pending = summary == null ? 0L : summary.getTotalPendingMessages();
            metrics.set("linkforge.stream.pending", pending, "operation", operation);

            PendingMessages sample = pending <= 0
                    ? null
                    : redis.opsForStream().pending(streamKey, group, Range.unbounded(), Math.min(pending, 500L));
            long oldestIdleMillis = 0L;
            if (sample != null) {
                for (PendingMessage message : sample) {
                    Duration idle = message == null ? null : message.getElapsedTimeSinceLastDelivery();
                    if (idle != null) {
                        oldestIdleMillis = Math.max(oldestIdleMillis, idle.toMillis());
                    }
                }
            }
            metrics.set("linkforge.stream.oldest_pending_idle_millis", oldestIdleMillis, "operation", operation);

            Long lag = readGroupLag(streamKey);
            if (lag != null) {
                metrics.set("linkforge.stream.lag", Math.max(lag, 0L), "operation", operation);
            }
        } catch (Exception e) {
            metrics.increment("linkforge.stream.failures", "operation", operation, "stage", "observe");
            log.debug("{} stream metrics lookup failed: streamKey={}, err={}", operation, streamKey, e.getMessage());
        }
    }

    private Long readGroupLag(String streamKey) {
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
            for (int i = 0; i + 1 < fields.size(); i += 2) {
                String field = asText(fields.get(i));
                if ("name".equals(field)) {
                    name = asText(fields.get(i + 1));
                } else if ("lag".equals(field)) {
                    lag = asLong(fields.get(i + 1));
                }
            }
            if (group.equals(name)) {
                return lag;
            }
        }
        return null;
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
}
