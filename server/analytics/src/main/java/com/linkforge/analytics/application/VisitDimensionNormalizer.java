package com.linkforge.analytics.application;

import com.linkforge.contract.analytics.VisitContext;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

/**
 * 访问维度归一化工具：将 Referer / Accept-Language / UA / UTM 等高噪音输入转换为低基数维度值，
 * 用于统计聚合与明细落库（避免把完整 URL、超长 UA、敏感 query 等直接写入持久层）。
 */
final class VisitDimensionNormalizer {

    static final int DEFAULT_MAX_DIM_VALUE_LEN = 255;
    static final int DEFAULT_MAX_UA_RAW_LEN = 512;
    static final int DEFAULT_MAX_TRACKING_VALUE_LEN = 128;

    private static final int ABS_MAX_DIM_VALUE_LEN = 512;
    private static final int ABS_MAX_UA_RAW_LEN = 2048;
    private static final int ABS_MAX_TRACKING_VALUE_LEN = 512;
    private static final int ABS_MAX_REFERER_LEN = 2048;
    private static final int ABS_MAX_ACCEPT_LANGUAGE_LEN = 256;

    private VisitDimensionNormalizer() {
    }

    static Normalized normalize(VisitContext visitContext, int maxDimValueLen, int maxUaRawLen, int maxTrackingValueLen) {
        int safeMaxDimValueLen = normalizeMaxLen(maxDimValueLen, DEFAULT_MAX_DIM_VALUE_LEN, ABS_MAX_DIM_VALUE_LEN);
        int safeMaxUaRawLen = normalizeMaxLen(maxUaRawLen, DEFAULT_MAX_UA_RAW_LEN, ABS_MAX_UA_RAW_LEN);
        int safeMaxTrackingValueLen = normalizeMaxLen(maxTrackingValueLen, DEFAULT_MAX_TRACKING_VALUE_LEN, ABS_MAX_TRACKING_VALUE_LEN);

        String refererDomain = normalizeRefererDomain(visitContext == null ? null : visitContext.referer(), safeMaxDimValueLen);
        String language = normalizePrimaryLanguage(visitContext == null ? null : visitContext.acceptLanguage(), 32);

        String uaRaw = truncate(cleanInline(visitContext == null ? null : visitContext.userAgent(), safeMaxUaRawLen), safeMaxUaRawLen);
        UaInfo ua = parseUserAgent(uaRaw);

        Tracking t = extractTracking(visitContext == null ? null : visitContext.trackingParams(), safeMaxTrackingValueLen);

        return new Normalized(
                refererDomain,
                language,
                uaRaw,
                ua.uaFamily(),
                ua.osFamily(),
                ua.deviceType(),
                t.utmSource(),
                t.utmMedium(),
                t.utmCampaign()
        );
    }

    static String normalizeRefererDomain(String referer, int maxLen) {
        String r = truncate(referer, ABS_MAX_REFERER_LEN);
        if (r == null) {
            // 浏览器/策略可能不带 Referer：统一记为 direct（更符合运营语义）
            return "direct";
        }
        try {
            URI u = URI.create(r);
            String host = u.getHost();
            if (host == null || host.isBlank()) {
                return "unknown";
            }
            return truncate(host.toLowerCase(Locale.ROOT), maxLen);
        } catch (Exception e) {
            return "unknown";
        }
    }

    static String normalizePrimaryLanguage(String acceptLanguage, int maxLen) {
        String raw = truncate(acceptLanguage, ABS_MAX_ACCEPT_LANGUAGE_LEN);
        if (raw == null) {
            return "unknown";
        }
        // 例如：zh-CN,zh;q=0.9,en;q=0.8 → zh-cn
        String first = raw;
        int comma = raw.indexOf(',');
        if (comma >= 0) {
            first = raw.substring(0, comma);
        }
        String token = first;
        int semi = first.indexOf(';');
        if (semi >= 0) {
            token = first.substring(0, semi);
        }
        String t = trimToNull(token);
        if (t == null) {
            return "unknown";
        }
        t = t.replace('_', '-').toLowerCase(Locale.ROOT);
        // 仅保留字母/数字/-
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '-';
            if (ok) {
                sb.append(ch);
            }
        }
        String out = trimToNull(sb.toString());
        return out == null ? "unknown" : truncate(out, maxLen);
    }

    static UaInfo parseUserAgent(String userAgent) {
        String ua = trimToNull(userAgent);
        if (ua == null) {
            return new UaInfo("unknown", "unknown", "unknown");
        }
        String u = ua.toLowerCase(Locale.ROOT);

        boolean bot = containsAny(u, "bot", "crawler", "spider", "curl", "wget", "python-requests", "httpclient");
        if (bot) {
            return new UaInfo("bot", guessOs(u), "bot");
        }

        String uaFamily = guessBrowser(u);
        String osFamily = guessOs(u);
        String deviceType = guessDeviceType(u);
        return new UaInfo(uaFamily, osFamily, deviceType);
    }

    private static String guessBrowser(String u) {
        if (u == null || u.isBlank()) {
            return "unknown";
        }
        if (u.contains("edg/") || u.contains("edge/")) {
            return "edge";
        }
        if (u.contains("chrome/") && !u.contains("chromium")) {
            return "chrome";
        }
        if (u.contains("firefox/")) {
            return "firefox";
        }
        // Safari 通常与 Chrome 共存；需排除 chrome/ 与 crios（iOS Chrome）
        if ((u.contains("safari/") || u.contains("safari")) && !u.contains("chrome/") && !u.contains("crios")) {
            return "safari";
        }
        if (u.contains("msie") || u.contains("trident/")) {
            return "ie";
        }
        return "other";
    }

    private static String guessOs(String u) {
        if (u == null || u.isBlank()) {
            return "unknown";
        }
        if (u.contains("windows nt")) {
            return "windows";
        }
        if (u.contains("android")) {
            return "android";
        }
        if (u.contains("iphone") || u.contains("ipad") || u.contains("ipod")) {
            return "ios";
        }
        if (u.contains("mac os x") || u.contains("macintosh")) {
            return "macos";
        }
        if (u.contains("linux")) {
            return "linux";
        }
        return "other";
    }

    private static String guessDeviceType(String u) {
        if (u == null || u.isBlank()) {
            return "unknown";
        }
        if (u.contains("mobile")) {
            return "mobile";
        }
        if (u.contains("tablet") || u.contains("ipad")) {
            return "tablet";
        }
        return "desktop";
    }

    static Tracking extractTracking(Map<String, String> params, int maxValueLen) {
        if (params == null || params.isEmpty()) {
            return new Tracking(null, null, null);
        }
        String source = sanitizeTrackingValue(params.get("utm_source"), maxValueLen);
        String medium = sanitizeTrackingValue(params.get("utm_medium"), maxValueLen);
        String campaign = sanitizeTrackingValue(params.get("utm_campaign"), maxValueLen);
        return new Tracking(source, medium, campaign);
    }

    static String sanitizeTrackingValue(String raw, int maxLen) {
        int safeMax = normalizeMaxLen(maxLen, DEFAULT_MAX_TRACKING_VALUE_LEN, ABS_MAX_TRACKING_VALUE_LEN);
        String s = truncate(cleanInline(raw, safeMax), safeMax);
        if (s == null) {
            return null;
        }
        // 过于离散的值会导致维度爆炸：对空白/全符号等做一次收敛
        boolean hasAlnum = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch >= '0' && ch <= '9')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z')) {
                hasAlnum = true;
                break;
            }
        }
        return hasAlnum ? s : null;
    }

    private static String cleanInline(String v, int maxScanLen) {
        if (v == null) {
            return null;
        }
        int limit = v.length();
        if (maxScanLen > 0 && maxScanLen < limit) {
            limit = maxScanLen;
        }
        StringBuilder sb = new StringBuilder(Math.max(limit, 0));
        for (int i = 0; i < limit; i++) {
            char ch = v.charAt(i);
            if (ch == '\n' || ch == '\r' || ch == '\t') {
                sb.append(' ');
            } else if (ch < 0x20) {
                // 过滤其他控制字符
                sb.append(' ');
            } else {
                sb.append(ch);
            }
        }
        return trimToNull(sb.toString());
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isBlank() ? null : t;
    }

    private static String truncate(String v, int maxLen) {
        String t = trimToNull(v);
        if (t == null) {
            return null;
        }
        if (maxLen <= 0) {
            return t;
        }
        return t.length() <= maxLen ? t : t.substring(0, maxLen);
    }

    private static int normalizeMaxLen(int v, int dft, int cap) {
        int out = v <= 0 ? dft : v;
        if (cap > 0 && out > cap) {
            out = cap;
        }
        return out;
    }

    private static boolean containsAny(String haystack, String... needles) {
        if (haystack == null || haystack.isBlank() || needles == null || needles.length == 0) {
            return false;
        }
        for (String n : needles) {
            if (n != null && !n.isBlank() && haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }

    record UaInfo(String uaFamily, String osFamily, String deviceType) {
    }

    record Tracking(String utmSource, String utmMedium, String utmCampaign) {
    }

    record Normalized(
            String refererDomain,
            String language,
            String userAgentRaw,
            String userAgentFamily,
            String osFamily,
            String deviceType,
            String utmSource,
            String utmMedium,
            String utmCampaign
    ) {
    }
}
