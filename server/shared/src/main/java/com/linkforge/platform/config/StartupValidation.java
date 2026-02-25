package com.linkforge.platform.config;

import org.slf4j.Logger;

import java.util.List;

/**
 * 启动期配置校验（共享工具）。
 *
 * <p>目标：API/Edge 复用同一套“格式与边界”规则，减少长期漂移点。</p>
 */
public final class StartupValidation {

    private StartupValidation() {
    }

    public static void validateRedirectBasics(AppProperties properties, List<String> errors) {
        if (properties == null || properties.getRedirect() == null) {
            errors.add("redirect 配置缺失");
            return;
        }
        int status = properties.getRedirect().getDefaultStatusCode();
        if (status != 301 && status != 302) {
            errors.add("app.redirect.default-status-code 仅支持 301/302");
        }
        if (properties.getRedirect().getCacheTtlSeconds() <= 0) {
            errors.add("app.redirect.cache-ttl-seconds 必须 > 0");
        }
    }

    public static void validateAnalyticsBasics(AppProperties properties, boolean strict, Logger log, List<String> errors) {
        if (properties == null || properties.getAnalytics() == null) {
            errors.add("analytics 配置缺失");
            return;
        }

        String salt = properties.getAnalytics().getSalt();
        if (isBlank(salt)) {
            errors.add("app.analytics.salt 不能为空（用于访客指纹 hash）");
        } else if (looksLikeDev(salt)) {
            if (strict) {
                errors.add("analytics salt 看起来像开发默认值，请在生产环境覆盖 ANALYTICS_SALT");
            } else if (log != null) {
                log.warn("analytics salt 使用了疑似开发默认值；生产环境请覆盖 ANALYTICS_SALT");
            }
        }

        if (properties.getAnalytics().getRedisKeyTtlDays() <= 0) {
            errors.add("app.analytics.redis-key-ttl-days 必须 > 0");
        }
    }

    public static void validateAnalyticsTrackingAllowlist(AppProperties properties, List<String> errors) {
        if (properties == null || properties.getAnalytics() == null) {
            return;
        }
        var allowlist = properties.getAnalytics().getTrackingParamAllowlist();
        if (allowlist == null) {
            return;
        }
        for (String p : allowlist) {
            String v = trimToNull(p);
            if (v == null) {
                continue;
            }
            if (!isValidParamPattern(v)) {
                errors.add("app.analytics.tracking-param-allowlist 包含不合法项: " + v);
                break;
            }
        }
    }

    public static void validateAnalyticsDimensionsTypes(AppProperties properties, List<String> errors) {
        if (properties == null || properties.getAnalytics() == null) {
            return;
        }
        var dims = properties.getAnalytics().getDimensions();
        if (dims == null || !dims.isEnabled()) {
            return;
        }
        var types = dims.getTypes();
        if (types == null) {
            return;
        }
        for (String t : types) {
            String v = trimToNull(t);
            if (v == null) {
                continue;
            }
            // 维度类型仅允许小写字母/数字/下划线，避免 Redis key 异常
            String s = v.trim();
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                boolean ok = (ch >= '0' && ch <= '9')
                        || (ch >= 'a' && ch <= 'z')
                        || ch == '_';
                if (!ok) {
                    errors.add("app.analytics.dimensions.types 包含不合法项: " + v);
                    break;
                }
            }
        }
    }

    public static void validateAnalyticsEvents(AppProperties properties, List<String> errors) {
        if (properties == null || properties.getAnalytics() == null) {
            return;
        }
        var ev = properties.getAnalytics().getEvents();
        if (ev == null || !ev.isEnabled()) {
            return;
        }
        double r = ev.getSampleRate();
        if (r < 0 || r > 1) {
            errors.add("app.analytics.events.sample-rate 必须在 0~1 之间");
        }
        if (ev.getMaxUserAgentLength() < 0) {
            errors.add("app.analytics.events.max-user-agent-length 必须 >= 0");
        }
        if (ev.getMaxTrackingValueLength() < 0) {
            errors.add("app.analytics.events.max-tracking-value-length 必须 >= 0");
        }
        if (ev.getRetentionDays() < 0) {
            errors.add("app.analytics.events.retention-days 必须 >= 0");
        }
    }

    public static boolean looksLikeDev(String v) {
        String t = v.trim().toLowerCase();
        return t.contains("dev-change-me")
                || t.contains("please_set_")
                || t.contains("please-set-")
                || t.contains("change-me");
    }

    public static boolean isBlank(String v) {
        return v == null || v.trim().isBlank();
    }

    public static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isBlank() ? null : t;
    }

    public static boolean isValidParamPattern(String p) {
        if (p == null || p.isBlank()) {
            return false;
        }
        String t = p.trim();
        if ("*".equals(t)) {
            return false;
        }
        boolean star = t.endsWith("*");
        String base = star ? t.substring(0, t.length() - 1) : t;
        if (base.isBlank()) {
            return false;
        }
        for (int i = 0; i < base.length(); i++) {
            char ch = base.charAt(i);
            boolean ok = (ch >= '0' && ch <= '9')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z')
                    || ch == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}

