package com.linkforge.api;

import com.linkforge.platform.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class ApiStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ApiStartupValidator.class);

    private static final Set<String> SAME_SITE = Set.of("Lax", "Strict", "None");

    private final Environment env;
    private final AppProperties properties;

    public ApiStartupValidator(Environment env, AppProperties properties) {
        this.env = env;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean strict = env.acceptsProfiles(Profiles.of("prod"))
                || env.getProperty("app.strict-config", Boolean.class, false);

        List<String> errors = new ArrayList<>();

        if (isBlank(properties.getBaseUrl())) {
            errors.add("app.base-url 不能为空（用于拼接 shortUrl）");
        }

        // redirect/cache 配置校验（API 服务也会写入 LinkMeta 缓存，需保证 TTL 合法）
        try {
            int status = properties.getRedirect().getDefaultStatusCode();
            if (status != 301 && status != 302) {
                errors.add("app.redirect.default-status-code 仅支持 301/302");
            }
            if (properties.getRedirect().getCacheTtlSeconds() <= 0) {
                errors.add("app.redirect.cache-ttl-seconds 必须 > 0");
            }
        } catch (Exception e) {
            errors.add("redirect 配置读取失败: " + e.getMessage());
        }

        String jwtSecret = properties.getSecurity().getJwt().getSecret();
        if (isBlank(jwtSecret)) {
            errors.add("app.security.jwt.secret 不能为空（用于签发/校验 JWT）");
        } else if (looksLikeDev(jwtSecret)) {
            if (strict) {
                errors.add("JWT secret 看起来像开发默认值，请在生产环境覆盖 JWT_SECRET");
            } else {
                log.warn("JWT secret 使用了疑似开发默认值；生产环境请覆盖 JWT_SECRET");
            }
        }

        String salt = properties.getAnalytics().getSalt();
        if (isBlank(salt)) {
            errors.add("app.analytics.salt 不能为空（用于访客指纹 hash）");
        } else if (looksLikeDev(salt)) {
            if (strict) {
                errors.add("analytics salt 看起来像开发默认值，请在生产环境覆盖 ANALYTICS_SALT");
            } else {
                log.warn("analytics salt 使用了疑似开发默认值；生产环境请覆盖 ANALYTICS_SALT");
            }
        }
        if (properties.getAnalytics().getRedisKeyTtlDays() <= 0) {
            errors.add("app.analytics.redis-key-ttl-days 必须 > 0");
        }

        // analytics 追踪参数采集白名单（可选）：仅做格式校验，避免误配把敏感 query 带入统计
        try {
            var allowlist = properties.getAnalytics().getTrackingParamAllowlist();
            if (allowlist != null) {
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

            var dims = properties.getAnalytics().getDimensions();
            if (dims != null && dims.isEnabled()) {
                var types = dims.getTypes();
                if (types != null) {
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
            }

            var ev = properties.getAnalytics().getEvents();
            if (ev != null && ev.isEnabled()) {
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
        } catch (Exception e) {
            errors.add("analytics 配置校验异常: " + e.getMessage());
        }

        if (properties.getSecurity().getJwt().isCookieEnabled()) {
            String cookieName = properties.getSecurity().getJwt().getCookieName();
            if (isBlank(cookieName)) {
                errors.add("cookie 模式已开启，但 app.security.jwt.cookie-name 为空");
            }
            String sameSite = properties.getSecurity().getJwt().getCookieSameSite();
            if (!isBlank(sameSite) && !SAME_SITE.contains(sameSite.trim())) {
                errors.add("app.security.jwt.cookie-same-site 仅支持 Lax/Strict/None");
            }
            if (strict && !properties.getSecurity().getJwt().isCookieSecure()) {
                errors.add("生产环境 cookie 模式建议开启 app.security.jwt.cookie-secure=true");
            }
        }

        // CORS：当允许凭证（cookie）时必须使用明确白名单
        if (properties.getCors() != null && properties.getCors().isAllowCredentials()) {
            var origins = properties.getCors().getAllowedOrigins();
            boolean hasWildcard = origins != null && origins.stream().anyMatch(o -> o != null && o.trim().equals("*"));
            boolean hasNonBlank = origins != null && origins.stream().anyMatch(o -> o != null && !o.trim().isBlank());
            if (!hasNonBlank) {
                errors.add("CORS allowCredentials=true 时必须配置 app.cors.allowed-origins 白名单");
            }
            if (hasWildcard) {
                errors.add("CORS allowCredentials=true 时禁止 allowed-origins 包含 \"*\"");
            }
        }

        // Redirect 体验增强配置（可选）：模式/白名单仅做格式校验，避免配置错误影响 Edge 行为
        try {
            String mode = trimToNull(properties.getRedirect().getQueryForwardMode());
            if (mode != null) {
                String t = mode.trim().toUpperCase();
                if (!("OFF".equals(t) || "ALLOWLIST".equals(t) || "ALL".equals(t))) {
                    errors.add("app.redirect.query-forward-mode 仅支持 OFF/ALLOWLIST/ALL");
                }
            }
            var allowlist = properties.getRedirect().getQueryForwardAllowlist();
            if (allowlist != null) {
                for (String p : allowlist) {
                    String v = trimToNull(p);
                    if (v == null) {
                        continue;
                    }
                    if (!isValidParamPattern(v)) {
                        errors.add("app.redirect.query-forward-allowlist 包含不合法项: " + v);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            errors.add("redirect 体验配置校验异常: " + e.getMessage());
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("启动配置校验失败: " + String.join("; ", errors));
        }
    }

    private static boolean looksLikeDev(String v) {
        String t = v.trim().toLowerCase();
        return t.contains("dev-change-me")
                || t.contains("please_set_")
                || t.contains("please-set-")
                || t.contains("change-me");
    }

    private static boolean isBlank(String v) {
        return v == null || v.trim().isBlank();
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isBlank() ? null : t;
    }

    private static boolean isValidParamPattern(String p) {
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
