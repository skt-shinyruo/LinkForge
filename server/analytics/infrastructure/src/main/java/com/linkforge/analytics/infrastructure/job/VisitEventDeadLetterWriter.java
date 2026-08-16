package com.linkforge.analytics.infrastructure.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.LinkVisitEventInsertRow;
import com.linkforge.foundation.observability.OperationalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将不可写入的访问明细复制到同源 Stream 的诊断 DLQ。
 *
 * <p>DLQ key 固定为 {@code {visitStream}:dlq}，仅保留定位所需的 streamId、租户、链接、requestId、
 * 分类原因和截断后的错误文本，不复制原始 UA/IP 等敏感字段。只有 DLQ 记录成功写入后，上游才能 ACK
 * poison record；写入失败返回 {@code false}，让原消息留在 pending 等待重试。裁剪与容量指标是写入后的
 * best-effort 维护，不影响已持久化记录的成功结果。</p>
 */
@Component
public class VisitEventDeadLetterWriter {

    private static final Logger log = LoggerFactory.getLogger(VisitEventDeadLetterWriter.class);

    private static final long DLQ_MAX_LEN = 10_000L;
    private static final String DLQ_SUFFIX = ":dlq";
    private static final int MAX_REQUEST_ID_LEN = 64;

    private final StringRedisTemplate redis;
    private final OperationalMetrics metrics;

    public VisitEventDeadLetterWriter(StringRedisTemplate redis) {
        this(redis, OperationalMetrics.noop());
    }

    @Autowired
    public VisitEventDeadLetterWriter(StringRedisTemplate redis, OperationalMetrics metrics) {
        this.redis = redis;
        this.metrics = metrics == null ? OperationalMetrics.noop() : metrics;
    }

    /**
     * 记录一条数据完整性错误，不向调用方传播 Redis 异常。
     *
     * @return DLQ 记录已经写入时返回 {@code true}；参数无效或写入失败时返回 {@code false}
     */
    public boolean write(String streamKey, RecordId recordId, LinkVisitEventInsertRow row, Exception error) {
        if (redis == null || streamKey == null || streamKey.isBlank() || row == null) {
            return false;
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
            RecordId written = redis.opsForStream().add(StreamRecords.newRecord().in(dlqKey).ofStrings(fields));
            if (written == null) {
                metrics.increment("linkforge.dead_letter.events", "source", "analytics_visit_ingest", "result", "failure");
                return false;
            }
        } catch (Exception ex) {
            metrics.increment("linkforge.dead_letter.events", "source", "analytics_visit_ingest", "result", "failure");
            log.warn("dead-letter write failed: streamId={}, err={}", recordId, ex.getMessage());
            return false;
        }

        metrics.increment("linkforge.dead_letter.events", "source", "analytics_visit_ingest", "result", "written");
        try {
            redis.opsForStream().trim(dlqKey, DLQ_MAX_LEN, true);
            Long size = redis.opsForStream().size(dlqKey);
            if (size != null) {
                metrics.set("linkforge.dead_letter.size", size, "source", "analytics_visit_ingest");
            }
        } catch (Exception ex) {
            metrics.increment("linkforge.dead_letter.maintenance_failures", "source", "analytics_visit_ingest");
            log.debug("dead-letter maintenance failed: streamId={}, err={}", recordId, ex.getMessage());
        }
        return true;
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
