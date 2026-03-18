package com.linkforge.analytics.infrastructure.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.LinkVisitEventInsertRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class VisitEventDeadLetterWriter {

    private static final Logger log = LoggerFactory.getLogger(VisitEventDeadLetterWriter.class);

    private static final long DLQ_MAX_LEN = 10_000L;
    private static final String DLQ_SUFFIX = ":dlq";
    private static final int MAX_REQUEST_ID_LEN = 64;

    private final StringRedisTemplate redis;

    public VisitEventDeadLetterWriter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void write(String streamKey, RecordId recordId, LinkVisitEventInsertRow row, Exception error) {
        if (redis == null || streamKey == null || streamKey.isBlank() || row == null) {
            return;
        }
        String dlqKey = streamKey + DLQ_SUFFIX;

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ts", String.valueOf(System.currentTimeMillis()));
        fields.put("streamId", recordId == null ? "" : recordId.toString());
        fields.put("tenantId", row.getTenantId() == null ? "" : String.valueOf(row.getTenantId()));
        fields.put("linkId", row.getLinkId() == null ? "" : String.valueOf(row.getLinkId()));
        fields.put("requestId", truncate(row.getRequestId(), MAX_REQUEST_ID_LEN));
        fields.put("reason", "data_integrity");
        fields.put("err", truncate(error == null ? null : error.getMessage(), 200));

        try {
            redis.opsForStream().add(StreamRecords.newRecord().in(dlqKey).ofStrings(fields));
            redis.opsForStream().trim(dlqKey, DLQ_MAX_LEN, true);
        } catch (Exception ex) {
            log.debug("dead-letter write failed: streamId={}, err={}", recordId, ex.getMessage());
        }
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
}
