package com.linkforge.contract.analytics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

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

    public static String statsDirtyStreamKey(LocalDate day) {
        return "stats:dirty:flush:" + DAY.format(day);
    }

    public static String dimDirtyStreamKey(LocalDate day) {
        return "stats:dirty:dim:" + DAY.format(day);
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
        String t = normalizeDimType(dimType);
        return "stats:dim:pv:" + tenantId + ":" + linkId + ":" + DAY.format(day) + ":" + t;
    }

    /**
     * 维度 UV 计数（HyperLogLog）：每个 dimType + dimValue 对应一个 HLL key。
     *
     * <p>约束：dimValue 可能包含不可控字符或过长，为避免 key 歧义与长度膨胀，使用 SHA-256(hex) 作为后缀。</p>
     *
     * <p>示例：stats:dim:uv:{tenantId}:{linkId}:{yyyyMMdd}:{dimType}:{dimValueSha256}</p>
     */
    public static String dimUvHllKey(long tenantId, long linkId, LocalDate day, String dimType, String dimValue) {
        String t = normalizeDimType(dimType);
        String v = dimValue == null ? "" : dimValue.trim();
        // 维度 value 参与哈希前做一次防御性长度 cap（避免异常输入导致过高 CPU 开销）
        if (v.length() > 512) {
            v = v.substring(0, 512);
        }
        return "stats:dim:uv:" + tenantId + ":" + linkId + ":" + DAY.format(day) + ":" + t + ":" + sha256Hex(v);
    }

    /**
     * 访问明细事件流（Redis Stream）。
     */
    public static String visitEventStreamKey() {
        return "stats:visit:events";
    }

    private static String normalizeDimType(String dimType) {
        String t = dimType == null ? "unknown" : dimType.trim().toLowerCase();
        if (t.isBlank()) {
            t = "unknown";
        }
        // 维度类型仅用于内部 key，避免出现 ':' 导致歧义
        return t.replace(':', '_');
    }

    private static String sha256Hex(String s) {
        String raw = s == null ? "" : s;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}
