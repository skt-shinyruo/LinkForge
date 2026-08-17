package com.linkforge.foundation.config;

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

    /**
     * 校验 Snowflake 节点号及 strict 模式下的默认节点组合。
     *
     * <p>本方法只向 {@code errors} 追加问题，不抛异常；{@code strict} 为 true 时拒绝本地兼容默认值 1/1，
     * 非 strict 模式保留该值而不记录告警。</p>
     */
    public static void validateIdBasics(IdProperties properties, boolean strict, Logger log, List<String> errors) {
        if (properties == null) {
            errors.add("id 配置缺失");
            return;
        }

        long workerId = properties.getWorkerId();
        long datacenterId = properties.getDatacenterId();

        // Snowflake 的 workerId/datacenterId 均占 5 bit，合法范围为 0..31。
        if (workerId < 0 || workerId > 31) {
            errors.add("app.id.worker-id 仅支持 0~31");
        }
        if (datacenterId < 0 || datacenterId > 31) {
            errors.add("app.id.datacenter-id 仅支持 0~31");
        }

        // 多实例部署护栏：避免“默认值忘改”导致 ID 冲突、主键冲突或数据错写。
        if (strict && workerId == 1L && datacenterId == 1L) {
            errors.add("生产/strict 模式禁止使用默认 app.id.worker-id=1 且 app.id.datacenter-id=1；多实例部署会发生 ID 冲突，请显式配置（例如通过 ID_WORKER_ID/ID_DATACENTER_ID）");
        }

        // 非严格模式保持静默，避免本地开发日志噪音；强约束由 app.strict-config 或 prod profile 开启。
        if (!strict && workerId == 1L && datacenterId == 1L && log != null) {
            // 有意保持无操作。
        }
    }

    /** 校验 Redirect 默认状态码和缓存 TTL 的基本边界；只追加错误，不负责加载或修复配置。 */
    public static void validateRedirectBasics(RedirectProperties properties, List<String> errors) {
        if (properties == null) {
            errors.add("redirect 配置缺失");
            return;
        }
        int status = properties.getDefaultStatusCode();
        if (status != 301 && status != 302) {
            errors.add("app.redirect.default-status-code 仅支持 301/302");
        }
        if (properties.getCacheTtlSeconds() <= 0) {
            errors.add("app.redirect.cache-ttl-seconds 必须 > 0");
        }
        if (properties.getNotFoundCacheTtlSeconds() < 0) {
            errors.add("app.redirect.not-found-cache-ttl-seconds 必须 >= 0");
        }
    }

    /**
     * 校验 Analytics 盐和 Redis 生命周期。
     *
     * <p>明显开发盐在 strict 模式作为错误，在非 strict 模式仅写 warn；本方法不记录盐原文。</p>
     */
    public static void validateAnalyticsBasics(AnalyticsProperties properties, boolean strict, Logger log, List<String> errors) {
        if (properties == null) {
            errors.add("analytics 配置缺失");
            return;
        }

        String salt = properties.getSalt();
        if (isBlank(salt)) {
            errors.add("app.analytics.salt 不能为空（用于访客指纹 hash）");
        } else if (looksLikeDev(salt)) {
            if (strict) {
                errors.add("analytics salt 看起来像开发默认值，请在生产环境覆盖 ANALYTICS_SALT");
            } else if (log != null) {
                log.warn("analytics salt 使用了疑似开发默认值；生产环境请覆盖 ANALYTICS_SALT");
            }
        }

        if (properties.getRedisKeyTtlDays() <= 0) {
            errors.add("app.analytics.redis-key-ttl-days 必须 > 0");
        }
    }

    /** 判断非空配置值是否含有已知开发占位片段；用于提示而非密码强度评估。 */
    public static boolean looksLikeDev(String v) {
        String t = v.trim().toLowerCase();
        return t.contains("dev-change-me")
                || t.contains("please_set_")
                || t.contains("please-set-")
                || t.contains("change-me");
    }

    /** 返回值是否为 {@code null}、空字符串或仅由空白组成。 */
    public static boolean isBlank(String v) {
        return v == null || v.trim().isBlank();
    }

    /** 去除首尾空白；空结果标准化为 {@code null}。 */
    public static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isBlank() ? null : t;
    }

    /** 参数名或末尾带单个通配符的参数前缀。 */
    public static boolean isValidParamPattern(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String pattern = value.trim();
        if ("*".equals(pattern)) {
            return false;
        }
        String base = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) : pattern;
        if (base.isBlank()) {
            return false;
        }
        for (int i = 0; i < base.length(); i++) {
            char ch = base.charAt(i);
            boolean alphanumeric = ch >= '0' && ch <= '9'
                    || ch >= 'A' && ch <= 'Z'
                    || ch >= 'a' && ch <= 'z';
            if (!(alphanumeric || ch == '_')) {
                return false;
            }
        }
        return true;
    }

}
