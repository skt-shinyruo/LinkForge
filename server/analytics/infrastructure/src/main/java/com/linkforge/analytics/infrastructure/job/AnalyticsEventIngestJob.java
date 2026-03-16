package com.linkforge.analytics.infrastructure.job;

import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkVisitEventInsertRow;
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
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private static final long DLQ_MAX_LEN = 10_000L;
    private static final String DLQ_SUFFIX = ":dlq";

    private static final int MAX_REQUEST_ID_LEN = 64;
    private static final int MAX_IP_HASH_LEN = 64;
    private static final int MAX_UA_RAW_LEN = 512;
    private static final int MAX_UA_FAMILY_LEN = 64;
    private static final int MAX_OS_FAMILY_LEN = 64;
    private static final int MAX_DEVICE_TYPE_LEN = 32;
    private static final int MAX_REFERER_DOMAIN_LEN = 255;
    private static final int MAX_LANGUAGE_LEN = 32;
    private static final int MAX_UTM_VALUE_LEN = 128;

    private final StringRedisTemplate redis;
    private final LinkVisitEventMapper visitEventMapper;
    private final AnalyticsProperties analyticsProperties;
    private final IdProperties idProperties;
    private final SnowflakeIdGenerator idGenerator;
    private final String consumerName;

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

    record IngestItem(RecordId recordId, LinkVisitEventInsertRow row) {
    }

    void ingestRecords(String streamKey, List<MapRecord<String, Object, Object>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        List<IngestItem> items = new ArrayList<>(records.size());
        List<RecordId> ackAlways = new ArrayList<>(Math.min(records.size(), 200));

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
            if (requestId == null || requestId.length() > MAX_REQUEST_ID_LEN) {
                ackAlways.add(r.getId());
                continue;
            }

            long ts = safeLong(v.get("ts"), System.currentTimeMillis());
            LocalDateTime occurredAt = Instant.ofEpochMilli(ts).atOffset(ZoneOffset.UTC).toLocalDateTime();
            LinkVisitEventInsertRow row = new LinkVisitEventInsertRow();
            row.setId(idGenerator.nextId());
            row.setTenantId(tenantId);
            row.setLinkId(linkId);
            row.setOccurredAt(occurredAt);
            row.setRequestId(requestId);
            row.setIpHash(truncate(trimToNull(v.get("ipHash")), MAX_IP_HASH_LEN));
            row.setUaRaw(truncate(trimToNull(v.get("uaRaw")), MAX_UA_RAW_LEN));
            row.setUaFamily(truncate(trimToNull(v.get("uaFamily")), MAX_UA_FAMILY_LEN));
            row.setOsFamily(truncate(trimToNull(v.get("osFamily")), MAX_OS_FAMILY_LEN));
            row.setDeviceType(truncate(trimToNull(v.get("deviceType")), MAX_DEVICE_TYPE_LEN));
            row.setRefererDomain(truncate(trimToNull(v.get("refererDomain")), MAX_REFERER_DOMAIN_LEN));
            row.setLanguage(truncate(trimToNull(v.get("language")), MAX_LANGUAGE_LEN));
            row.setUtmSource(truncate(trimToNull(v.get("utmSource")), MAX_UTM_VALUE_LEN));
            row.setUtmMedium(truncate(trimToNull(v.get("utmMedium")), MAX_UTM_VALUE_LEN));
            row.setUtmCampaign(truncate(trimToNull(v.get("utmCampaign")), MAX_UTM_VALUE_LEN));
            items.add(new IngestItem(r.getId(), row));
        }

        if (items.isEmpty()) {
            acknowledge(streamKey, ackAlways);
            return;
        }

        try {
            visitEventMapper.batchInsertIgnore(items.stream().map(IngestItem::row).toList());
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
        acknowledge(streamKey, items.stream().map(IngestItem::recordId).toList());
    }

    private void isolatePoisonAndAck(String streamKey, List<IngestItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<RecordId> ackIds = new ArrayList<>(items.size());
        for (IngestItem item : items) {
            if (item == null || item.recordId() == null || item.row() == null) {
                continue;
            }
            try {
                // Fallback to per-row insert: isolate the poison record so the stream does not get stuck pending.
                visitEventMapper.batchInsertIgnore(List.of(item.row()));
                ackIds.add(item.recordId());
            } catch (DataIntegrityViolationException e) {
                deadLetter(streamKey, item, e);
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

    private void deadLetter(String streamKey, IngestItem item, Exception e) {
        if (redis == null || streamKey == null || streamKey.isBlank() || item == null || item.row() == null) {
            return;
        }
        String dlqKey = streamKey + DLQ_SUFFIX;

        LinkVisitEventInsertRow row = item.row();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ts", String.valueOf(System.currentTimeMillis()));
        fields.put("streamId", item.recordId() == null ? "" : item.recordId().toString());
        fields.put("tenantId", row.getTenantId() == null ? "" : String.valueOf(row.getTenantId()));
        fields.put("linkId", row.getLinkId() == null ? "" : String.valueOf(row.getLinkId()));
        fields.put("requestId", truncate(row.getRequestId(), MAX_REQUEST_ID_LEN));
        fields.put("reason", "data_integrity");
        fields.put("err", truncate(e == null ? null : e.getMessage(), 200));

        try {
            redis.opsForStream().add(StreamRecords.newRecord().in(dlqKey).ofStrings(fields));
            redis.opsForStream().trim(dlqKey, DLQ_MAX_LEN, true);
        } catch (Exception ex) {
            // best-effort: never break ingestion on DLQ failure
            log.debug("dead-letter write failed: streamId={}, err={}", item.recordId(), ex.getMessage());
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

    private static String truncate(String v, int maxLen) {
        if (v == null) {
            return null;
        }
        if (maxLen <= 0) {
            return v;
        }
        return v.length() <= maxLen ? v : v.substring(0, maxLen);
    }
}
