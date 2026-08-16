package com.linkforge.analytics.infrastructure.job;

import com.linkforge.foundation.observability.OperationalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * 汇总一次调度窗口内的 legacy dirty Stream 状态。
 *
 * <p>{@code XLEN} 包含已经 ACK 但尚未裁剪的历史，因此只上报为 retained entries。真正剩余工作是
 * consumer group 的 {@code lag + pending}。汇总只使用固定的 marker 类型标签，避免把日期或 stream key
 * 带入指标维度。</p>
 */
final class LegacyDirtyStreamMetrics {

    private static final Logger log = LoggerFactory.getLogger(LegacyDirtyStreamMetrics.class);
    private static final long UNKNOWN = -1L;

    private final StringRedisTemplate redis;
    private final RedisStreamBatchConsumer consumer;
    private final OperationalMetrics metrics;

    LegacyDirtyStreamMetrics(
            StringRedisTemplate redis,
            RedisStreamBatchConsumer consumer,
            OperationalMetrics metrics
    ) {
        this.redis = redis;
        this.consumer = consumer;
        this.metrics = metrics == null ? OperationalMetrics.noop() : metrics;
    }

    Aggregate start(String marker) {
        return new Aggregate(marker);
    }

    final class Aggregate {

        private final String marker;
        private long retainedEntries;
        private long lag;
        private long pending;
        private long oldestPendingIdleMillis;
        private long oldestUnprocessedAgeMillis;
        private long newestWriteEpochMillis;
        private boolean retainedObserved = true;
        private boolean workObserved = true;
        private boolean lastWriteObserved = true;

        private Aggregate(String marker) {
            this.marker = marker;
        }

        void observe(String streamKey) {
            Long retained;
            try {
                retained = redis.opsForStream().size(streamKey);
            } catch (RuntimeException ex) {
                retainedObserved = false;
                workObserved = false;
                lastWriteObserved = false;
                observationFailed(streamKey, "retained_entries", ex);
                return;
            }
            if (retained == null) {
                retainedObserved = false;
                workObserved = false;
                lastWriteObserved = false;
                observationFailed(streamKey, "retained_entries", null);
                return;
            }

            long safeRetained = Math.max(retained, 0L);
            retainedEntries = saturatingAdd(retainedEntries, safeRetained);
            if (safeRetained == 0L) {
                try {
                    Boolean streamExists = redis.hasKey(streamKey);
                    if (streamExists == null) {
                        workObserved = false;
                        observationFailed(streamKey, "exists", null);
                        return;
                    }
                    if (!streamExists) {
                        return;
                    }
                } catch (RuntimeException ex) {
                    workObserved = false;
                    observationFailed(streamKey, "exists", ex);
                    return;
                }
            }

            if (safeRetained > 0L) {
                try {
                    List<MapRecord<String, Object, Object>> last = redis.opsForStream().reverseRange(
                            streamKey, Range.unbounded(), Limit.limit().count(1));
                    RecordId id = last == null || last.isEmpty() || last.get(0) == null ? null : last.get(0).getId();
                    Long timestamp = id == null ? null : id.getTimestamp();
                    if (timestamp != null && timestamp > 0L) {
                        newestWriteEpochMillis = Math.max(newestWriteEpochMillis, timestamp);
                    } else {
                        lastWriteObserved = false;
                        observationFailed(streamKey, "last_write", null);
                    }
                } catch (RuntimeException ex) {
                    lastWriteObserved = false;
                    observationFailed(streamKey, "last_write", ex);
                }
            }

            RedisStreamBatchConsumer.StreamState state = consumer.inspectStreamState(streamKey);
            if (!state.observed()) {
                workObserved = false;
                return;
            }
            lag = saturatingAdd(lag, state.lag());
            pending = saturatingAdd(pending, state.pending());
            oldestPendingIdleMillis = Math.max(oldestPendingIdleMillis, state.oldestPendingIdleMillis());
            oldestUnprocessedAgeMillis = Math.max(
                    oldestUnprocessedAgeMillis,
                    state.oldestUnprocessedAgeMillis()
            );
        }

        RedisStreamBatchConsumer.StreamState publish() {
            long remaining = workObserved ? saturatingAdd(lag, pending) : UNKNOWN;
            metrics.set("linkforge.analytics.dirty.legacy.retained_entries",
                    retainedObserved ? retainedEntries : UNKNOWN, "marker", marker);
            metrics.set("linkforge.analytics.dirty.legacy.lag", workObserved ? lag : UNKNOWN,
                    "marker", marker);
            metrics.set("linkforge.analytics.dirty.legacy.pending", workObserved ? pending : UNKNOWN,
                    "marker", marker);
            metrics.set("linkforge.analytics.dirty.legacy.remaining", remaining,
                    "marker", marker);
            metrics.set("linkforge.analytics.dirty.legacy.last_write_age_millis",
                    lastWriteObserved ? lastWriteAgeMillis() : UNKNOWN, "marker", marker);
            metrics.set("linkforge.analytics.dirty.legacy.observation_degraded",
                    retainedObserved && workObserved && lastWriteObserved ? 0L : 1L, "marker", marker);

            return workObserved
                    ? RedisStreamBatchConsumer.StreamState.observed(
                            lag,
                            pending,
                            oldestPendingIdleMillis,
                            oldestUnprocessedAgeMillis
                    )
                    : RedisStreamBatchConsumer.StreamState.unknown();
        }

        private long lastWriteAgeMillis() {
            return newestWriteEpochMillis == 0L
                    ? 0L
                    : Math.max(System.currentTimeMillis() - newestWriteEpochMillis, 0L);
        }

        private void observationFailed(String streamKey, String stage, RuntimeException error) {
            metrics.increment("linkforge.stream.failures",
                    "operation", "analytics_legacy_dirty_observe", "stage", stage);
            log.debug("legacy dirty stream observation failed: marker={}, streamKey={}, stage={}, err={}",
                    marker, streamKey, stage, error == null ? "unavailable" : error.getMessage());
        }
    }

    private static long saturatingAdd(long left, long right) {
        if (left >= Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
