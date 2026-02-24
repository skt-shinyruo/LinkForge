package com.linkforge.analytics.service;

import com.linkforge.platform.web.VisitInfo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;

final class VisitorFingerprint {

    private VisitorFingerprint() {
    }

    static String fingerprint(LocalDate day, VisitInfo visitInfo, String salt) {
        String ip = firstNonBlank(visitInfo == null ? null : visitInfo.ip());
        String ua = firstNonBlank(visitInfo == null ? null : visitInfo.userAgent());
        String raw = day + "|" + ip + "|" + ua + "|" + (salt == null ? "" : salt);
        return sha256Hex(raw);
    }

    /**
     * IP 哈希（稳定但不可逆）：用于访问明细的“排障关联”，避免落库明文 IP。
     *
     * <p>注意：该哈希不包含日期，便于跨天关联；如需更强匿名性可改为按天或按租户 salt。</p>
     */
    static String ipHash(VisitInfo visitInfo, String salt) {
        String ip = firstNonBlank(visitInfo == null ? null : visitInfo.ip());
        if (ip == null) {
            return null;
        }
        String raw = ip + "|" + (salt == null ? "" : salt);
        return sha256Hex(raw);
    }

    private static String firstNonBlank(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isBlank() ? null : t;
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
