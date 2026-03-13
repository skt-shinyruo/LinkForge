package com.linkforge.analytics.application;

import com.linkforge.contract.analytics.VisitContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;

final class VisitorFingerprint {

    private VisitorFingerprint() {
    }

    // Defensive caps: avoid letting attacker-controlled headers (e.g. User-Agent) blow up memory/CPU
    // when computing digest strings in the redirect hot path.
    private static final int MAX_FP_IP_LEN = 64;
    private static final int MAX_FP_UA_LEN = 512;
    private static final int MAX_FP_SALT_LEN = 128;

    static String fingerprint(LocalDate day, VisitContext visitContext, String salt) {
        String ip = firstNonBlankCapped(visitContext == null ? null : visitContext.ip(), MAX_FP_IP_LEN);
        String ua = firstNonBlankCapped(visitContext == null ? null : visitContext.userAgent(), MAX_FP_UA_LEN);
        String safeSalt = salt == null ? "" : capLen(salt, MAX_FP_SALT_LEN);
        String raw = day + "|" + ip + "|" + ua + "|" + safeSalt;
        return sha256Hex(raw);
    }

    /**
     * IP 哈希（稳定但不可逆）：用于访问明细的“排障关联”，避免落库明文 IP。
     *
     * <p>注意：该哈希不包含日期，便于跨天关联；如需更强匿名性可改为按天或按租户 salt。</p>
     */
    static String ipHash(VisitContext visitContext, String salt) {
        String ip = firstNonBlankCapped(visitContext == null ? null : visitContext.ip(), MAX_FP_IP_LEN);
        if (ip == null) {
            return null;
        }
        String safeSalt = salt == null ? "" : capLen(salt, MAX_FP_SALT_LEN);
        String raw = ip + "|" + safeSalt;
        return sha256Hex(raw);
    }

    private static String firstNonBlankCapped(String v, int maxLen) {
        if (v == null) {
            return null;
        }
        String t = capLen(v, maxLen).trim();
        return t.isBlank() ? null : t;
    }

    private static String capLen(String v, int maxLen) {
        if (v == null) {
            return null;
        }
        if (maxLen <= 0) {
            return v;
        }
        return v.length() <= maxLen ? v : v.substring(0, maxLen);
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
