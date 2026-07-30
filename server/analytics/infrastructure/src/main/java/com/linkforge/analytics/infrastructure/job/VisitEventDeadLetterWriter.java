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

/**
 * 将不可写入的访问明细复制到同源 Stream 的诊断 DLQ。
 *
 * <p>DLQ key 固定为 {@code {visitStream}:dlq}，仅保留定位所需的 streamId、租户、链接、requestId、
 * 分类原因和截断后的错误文本，不复制原始 UA/IP 等敏感字段。写入和裁剪均为 best-effort：失败只记日志，
 * 上游仍会 ACK 已确认的 poison record，以避免一个诊断设施故障阻塞消费组。</p>
 */
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

    /**
     * 尽力记录一条数据完整性错误，不向调用方传播 Redis 异常。
     */
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
