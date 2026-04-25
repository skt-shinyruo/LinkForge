package com.linkforge.analytics.infrastructure.job;

import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.foundation.config.AnalyticsProperties;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AnalyticsRedirectEventProjectorJob {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRedirectEventProjectorJob.class);

    private static final String GROUP = "lf-visit-projector";
    private static final String CONSUMER = "lf-visit-projector-consumer";
    private static final int BATCH_SIZE = 200;

    private final StringRedisTemplate redis;
    private final AnalyticsProperties analyticsProperties;
    private final AnalyticsRedisAggregateWriter aggregateWriter;

    public AnalyticsRedirectEventProjectorJob(
            StringRedisTemplate redis,
            AnalyticsProperties analyticsProperties,
            AnalyticsRedisAggregateWriter aggregateWriter
    ) {
        this.redis = redis;
        this.analyticsProperties = analyticsProperties;
        this.aggregateWriter = aggregateWriter;
    }

    @Scheduled(fixedDelayString = "${APP_ANALYTICS_REDIRECT_EVENT_PROJECTOR_DELAY_MS:2000}")
    @SchedulerLock(name = "lf:job:analytics:redirect-event-projector", lockAtMostFor = "PT2M")
    public void project() {
        AnalyticsProperties.Events cfg = analyticsProperties == null ? null : analyticsProperties.getEvents();
        if (cfg == null || !cfg.isEnabled()) {
            return;
        }

        String streamKey = AnalyticsKeys.visitEventStreamKey();
        if (!ensureGroup(streamKey)) {
            return;
        }

        Consumer consumer = Consumer.from(GROUP, CONSUMER);
        while (true) {
            List<MapRecord<String, Object, Object>> records = readSafe(
                    consumer,
                    StreamReadOptions.empty().count(BATCH_SIZE),
                    StreamOffset.create(streamKey, ReadOffset.from("0-0"))
            );
            if (records == null || records.isEmpty()) {
                records = readSafe(
                        consumer,
                        StreamReadOptions.empty().count(BATCH_SIZE),
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                );
            }
            if (records == null || records.isEmpty()) {
                return;
            }
            if (!projectRecords(streamKey, records)) {
                return;
            }
        }
    }

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
                ackIds.add(record.getId());
            } catch (Exception e) {
                log.warn("project analytics visit event failed: streamId={}, err={}", record.getId(), e.getMessage());
                acknowledge(streamKey, ackIds);
                return false;
            }
        }

        acknowledge(streamKey, ackIds);
        return true;
    }

    private boolean ensureGroup(String streamKey) {
        if (!hasKey(streamKey)) {
            return false;
        }
        try {
            redis.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), GROUP);
            return true;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("busygroup")) {
                return true;
            }
            log.debug("create analytics visit projector group failed: streamKey={}, err={}", streamKey, msg);
            return false;
        }
    }

    private boolean hasKey(String key) {
        try {
            Boolean exists = redis.hasKey(key);
            return exists != null && exists;
        } catch (Exception e) {
            return false;
        }
    }

    private List<MapRecord<String, Object, Object>> readSafe(
            Consumer consumer,
            StreamReadOptions options,
            StreamOffset<String> offset
    ) {
        try {
            return redis.opsForStream().read(consumer, options, offset);
        } catch (Exception e) {
            log.debug("read analytics visit projector stream failed: streamKey={}, err={}", offset == null ? null : offset.getKey(), e.getMessage());
            return null;
        }
    }

    private void acknowledge(String streamKey, List<RecordId> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try {
            redis.opsForStream().acknowledge(streamKey, GROUP, ids.toArray(new RecordId[0]));
        } catch (Exception e) {
            log.debug("ack analytics visit projector stream failed: streamKey={}, size={}, err={}", streamKey, ids.size(), e.getMessage());
        }
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
