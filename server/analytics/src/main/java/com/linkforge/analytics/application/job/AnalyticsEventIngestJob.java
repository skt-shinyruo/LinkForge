package com.linkforge.analytics.application.job;

import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.config.IdProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 访问明细事件落库作业：消费 Redis Stream 并批量写入 MySQL。
 *
 * <p>原则：best-effort，不影响主链路；失败时保留 pending，等待后续重试。</p>
 */
@Component
public class AnalyticsEventIngestJob {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventIngestJob.class);

    private static final String GROUP = "lf-visit-ingest";
    private static final Pattern NON_SAFE = Pattern.compile("[^a-zA-Z0-9._:-]");

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbcTemplate;
    private final AnalyticsProperties analyticsProperties;
    private final IdProperties idProperties;
    private final SnowflakeIdGenerator idGenerator;
    private final String consumerName;

    public AnalyticsEventIngestJob(
            StringRedisTemplate redis,
            JdbcTemplate jdbcTemplate,
            AnalyticsProperties analyticsProperties,
            IdProperties idProperties,
            SnowflakeIdGenerator idGenerator
    ) {
        this.redis = redis;
        this.jdbcTemplate = jdbcTemplate;
        this.analyticsProperties = analyticsProperties;
        this.idProperties = idProperties;
        this.idGenerator = idGenerator;
        this.consumerName = resolveConsumerName(analyticsProperties, idProperties);
    }

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

    private void ingestRecords(String streamKey, List<MapRecord<String, Object, Object>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO link_visit_events (
                  id, tenant_id, link_id, occurred_at, request_id,
                  ip_hash, ua_raw, ua_family, os_family, device_type,
                  referer_domain, language, utm_source, utm_medium, utm_campaign,
                  created_at
                ) VALUES (
                  ?, ?, ?, ?, ?,
                  ?, ?, ?, ?, ?,
                  ?, ?, ?, ?, ?,
                  NOW()
                )
                ON DUPLICATE KEY UPDATE id = id
                """;

        List<Object[]> batch = new ArrayList<>(records.size());
        List<RecordId> ackAlways = new ArrayList<>(Math.min(records.size(), 200));
        List<RecordId> ackAfterWrite = new ArrayList<>(Math.min(records.size(), 200));

        for (MapRecord<String, Object, Object> r : records) {
            if (r == null || r.getId() == null || r.getValue() == null) {
                continue;
            }
            Map<Object, Object> raw = r.getValue();
            // StringRedisTemplate 写入的 stream field/value 本质是 String 序列化，这里统一转为 String 处理
            Map<String, String> v = new java.util.HashMap<>(raw.size());
            for (Map.Entry<Object, Object> e : raw.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                v.put(String.valueOf(e.getKey()), e.getValue() == null ? null : String.valueOf(e.getValue()));
            }

            long tenantId = safeLong(v.get("tenantId"), -1);
            long linkId = safeLong(v.get("linkId"), -1);
            if (tenantId <= 0 || linkId <= 0) {
                ackAlways.add(r.getId());
                continue;
            }

            String requestId = trimToNull(v.get("requestId"));
            if (requestId == null) {
                ackAlways.add(r.getId());
                continue;
            }

            long ts = safeLong(v.get("ts"), System.currentTimeMillis());
            LocalDateTime occurredAt = Instant.ofEpochMilli(ts).atOffset(ZoneOffset.UTC).toLocalDateTime();

            batch.add(new Object[]{
                    idGenerator.nextId(),
                    tenantId,
                    linkId,
                    occurredAt,
                    requestId,
                    trimToNull(v.get("ipHash")),
                    trimToNull(v.get("uaRaw")),
                    trimToNull(v.get("uaFamily")),
                    trimToNull(v.get("osFamily")),
                    trimToNull(v.get("deviceType")),
                    trimToNull(v.get("refererDomain")),
                    trimToNull(v.get("language")),
                    trimToNull(v.get("utmSource")),
                    trimToNull(v.get("utmMedium")),
                    trimToNull(v.get("utmCampaign"))
            });
            ackAfterWrite.add(r.getId());
        }

        if (batch.isEmpty()) {
            acknowledge(streamKey, ackAlways);
            return;
        }

        try {
            jdbcTemplate.batchUpdate(sql, batch);
        } catch (DataAccessException e) {
            log.warn("ingest visit events failed: size={}, err={}", batch.size(), e.getMessage());
            acknowledge(streamKey, ackAlways);
            return;
        }

        if (!ackAlways.isEmpty()) {
            acknowledge(streamKey, ackAlways);
        }
        acknowledge(streamKey, ackAfterWrite);
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
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("busygroup")) {
                return true;
            }
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

    private static long safeLong(String raw, long defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isBlank() ? null : t;
    }
}
