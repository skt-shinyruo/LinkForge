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

    public record Batch(List<IngestItem> items, List<RecordId> ackAlways) {
    }

    public record IngestItem(RecordId recordId, LinkVisitEventInsertRow row) {
    }
}
