package com.linkforge.contract.analytics;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Analytics Redis key 契约（跨模块 SSOT）。
 *
 * <p>说明：Edge 写入，API flush/查询读取；变更需谨慎并配合契约测试锁定格式。</p>
 */
public final class AnalyticsKeys {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private AnalyticsKeys() {
    }

    public static String activeSetKey(LocalDate day) {
        return "stats:active:" + DAY.format(day);
    }

    public static String activeMember(long tenantId, long linkId) {
        return tenantId + ":" + linkId;
    }

    public static String pvKey(long tenantId, long linkId, LocalDate day) {
        return "stats:pv:" + tenantId + ":" + linkId + ":" + DAY.format(day);
    }

    public static String uvKey(long tenantId, long linkId, LocalDate day) {
        return "stats:uv:" + tenantId + ":" + linkId + ":" + DAY.format(day);
    }

    /**
     * 维度 PV 计数（Hash）：field=dimValue，value=pvCount。
     * <p>
     * 示例：stats:dim:pv:{tenantId}:{linkId}:{yyyyMMdd}:{dimType}
     */
    public static String dimPvHashKey(long tenantId, long linkId, LocalDate day, String dimType) {
        String t = dimType == null ? "unknown" : dimType.trim().toLowerCase();
        if (t.isBlank()) {
            t = "unknown";
        }
        // 维度类型仅用于内部 key，避免出现 ':' 导致歧义
        t = t.replace(':', '_');
        return "stats:dim:pv:" + tenantId + ":" + linkId + ":" + DAY.format(day) + ":" + t;
    }

    /**
     * 访问明细事件流（Redis Stream）。
     */
    public static String visitEventStreamKey() {
        return "stats:visit:events";
    }
}
