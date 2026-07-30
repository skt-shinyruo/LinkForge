package com.linkforge.analytics.infrastructure.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.LinkVisitEventInsertRow;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将不可信的 Redis Stream 字段转换为可写入访问明细表的受限行。
 *
 * <p>该边界负责验证 tenant/link/requestId，并对来自请求的维度和 UA 文本做长度截断，使数据库列约束
 * 不会把整批消费变成 poison batch。无效记录进入 {@link Batch#ackAlways()}：它们不具备重试价值，
 * 应由消费者确认而不是永久留在 pending。</p>
 *
 * <p>事件发生时间以毫秒转换为 UTC {@code LocalDateTime}；缺失或非法时间退回当前时间，因而不能将
 * Stream 明细用作严格审计时钟。</p>
 */
@Component
public class VisitEventBatchAssembler {

    private static final int MAX_REQUEST_ID_LEN = 64;
    private static final int MAX_IP_HASH_LEN = 64;
    private static final int MAX_UA_RAW_LEN = 512;
    private static final int MAX_UA_FAMILY_LEN = 64;
    private static final int MAX_OS_FAMILY_LEN = 64;
    private static final int MAX_DEVICE_TYPE_LEN = 32;
    private static final int MAX_REFERER_DOMAIN_LEN = 255;
    private static final int MAX_LANGUAGE_LEN = 32;
    private static final int MAX_UTM_VALUE_LEN = 128;

    private final SnowflakeIdGenerator idGenerator;

    public VisitEventBatchAssembler(SnowflakeIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    /**
     * 组装一批可插入项和必须直接 ACK 的无效项。
     *
     * @param records 来自同一 Redis Stream 的消息；空输入返回两个空列表
     */
    public Batch assemble(List<MapRecord<String, Object, Object>> records) {
        if (records == null || records.isEmpty()) {
            return new Batch(List.of(), List.of());
        }

        List<IngestItem> items = new ArrayList<>(records.size());
        List<RecordId> ackAlways = new ArrayList<>(Math.min(records.size(), 200));

        for (MapRecord<String, Object, Object> record : records) {
            if (record == null || record.getId() == null || record.getValue() == null) {
                continue;
            }
            Map<String, String> values = normalize(record.getValue());

            long tenantId = safeLong(values.get("tenantId"), -1);
            long linkId = safeLong(values.get("linkId"), -1);
            if (tenantId <= 0 || linkId <= 0) {
                ackAlways.add(record.getId());
                continue;
            }

            String requestId = trimToNull(values.get("requestId"));
            if (requestId == null || requestId.length() > MAX_REQUEST_ID_LEN) {
                ackAlways.add(record.getId());
                continue;
            }

            long ts = safeLong(values.get("ts"), System.currentTimeMillis());
            LocalDateTime occurredAt = Instant.ofEpochMilli(ts).atOffset(ZoneOffset.UTC).toLocalDateTime();

            LinkVisitEventInsertRow row = new LinkVisitEventInsertRow();
            row.setId(idGenerator.nextId());
            row.setTenantId(tenantId);
            row.setLinkId(linkId);
            row.setOccurredAt(occurredAt);
            row.setRequestId(requestId);
            row.setIpHash(truncate(trimToNull(values.get("ipHash")), MAX_IP_HASH_LEN));
            row.setUaRaw(truncate(trimToNull(values.get("uaRaw")), MAX_UA_RAW_LEN));
            row.setUaFamily(truncate(trimToNull(values.get("uaFamily")), MAX_UA_FAMILY_LEN));
            row.setOsFamily(truncate(trimToNull(values.get("osFamily")), MAX_OS_FAMILY_LEN));
            row.setDeviceType(truncate(trimToNull(values.get("deviceType")), MAX_DEVICE_TYPE_LEN));
            row.setRefererDomain(truncate(trimToNull(values.get("refererDomain")), MAX_REFERER_DOMAIN_LEN));
            row.setLanguage(truncate(trimToNull(values.get("language")), MAX_LANGUAGE_LEN));
            row.setUtmSource(truncate(trimToNull(values.get("utmSource")), MAX_UTM_VALUE_LEN));
            row.setUtmMedium(truncate(trimToNull(values.get("utmMedium")), MAX_UTM_VALUE_LEN));
            row.setUtmCampaign(truncate(trimToNull(values.get("utmCampaign")), MAX_UTM_VALUE_LEN));
            items.add(new IngestItem(record.getId(), row));
        }

        return new Batch(List.copyOf(items), List.copyOf(ackAlways));
    }

    private static Map<String, String> normalize(Map<Object, Object> raw) {
        Map<String, String> values = new java.util.HashMap<>(raw.size());
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            values.put(String.valueOf(entry.getKey()), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
        return values;
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (maxLen <= 0) {
            return value;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    /**
     * 消费批次的分流结果；{@code ackAlways} 中的消息不会尝试写入 MySQL。
     */
    public record Batch(List<IngestItem> items, List<RecordId> ackAlways) {
    }

    /**
     * 将原始 Stream record id 与已通过输入边界的数据库行绑定，供成功写入后 ACK。
     */
    public record IngestItem(RecordId recordId, LinkVisitEventInsertRow row) {
    }
}
