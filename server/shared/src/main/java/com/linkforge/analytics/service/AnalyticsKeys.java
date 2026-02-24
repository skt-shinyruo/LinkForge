package com.linkforge.analytics.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

final class AnalyticsKeys {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private AnalyticsKeys() {
    }

    static String activeSetKey(LocalDate day) {
        return "stats:active:" + DAY.format(day);
    }

    static String activeMember(long tenantId, long linkId) {
        return tenantId + ":" + linkId;
    }

    static String pvKey(long tenantId, long linkId, LocalDate day) {
        return "stats:pv:" + tenantId + ":" + linkId + ":" + DAY.format(day);
    }

    static String uvKey(long tenantId, long linkId, LocalDate day) {
        return "stats:uv:" + tenantId + ":" + linkId + ":" + DAY.format(day);
    }

    /**
     * 维度 PV 计数（Hash）：field=dimValue，value=pvCount。
     * <p>
     * 示例：stats:dim:pv:{tenantId}:{linkId}:{yyyyMMdd}:{dimType}
     */
    static String dimPvHashKey(long tenantId, long linkId, LocalDate day, String dimType) {
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
    static String visitEventStreamKey() {
        return "stats:visit:events";
    }
}
