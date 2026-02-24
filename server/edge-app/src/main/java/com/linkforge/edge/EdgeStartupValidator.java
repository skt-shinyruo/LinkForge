package com.linkforge.edge;

import com.linkforge.edge.net.CidrBlocks;
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

@Component
public class EdgeStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EdgeStartupValidator.class);

    private final Environment env;
    private final AppProperties properties;

    public EdgeStartupValidator(Environment env, AppProperties properties) {
        this.env = env;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean strict = env.acceptsProfiles(Profiles.of("prod"))
                || env.getProperty("app.strict-config", Boolean.class, false);

        List<String> errors = new ArrayList<>();

        int status = properties.getRedirect().getDefaultStatusCode();
        if (status != 301 && status != 302) {
            errors.add("app.redirect.default-status-code 仅支持 301/302");
        }
        if (properties.getRedirect().getCacheTtlSeconds() <= 0) {
            errors.add("app.redirect.cache-ttl-seconds 必须 > 0");
        }

        // Redirect 体验增强配置（可选）
        try {
            String notFoundLandingUrl = trimToNull(properties.getRedirect().getNotFoundLandingUrl());
            if (notFoundLandingUrl != null && !isHttpUrl(notFoundLandingUrl)) {
                errors.add("app.redirect.not-found-landing-url 必须为 http/https URL");
            }
            String goneLandingUrl = trimToNull(properties.getRedirect().getGoneLandingUrl());
            if (goneLandingUrl != null && !isHttpUrl(goneLandingUrl)) {
                errors.add("app.redirect.gone-landing-url 必须为 http/https URL");
            }

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
            var reserved = properties.getRedirect().getQueryForwardReservedParams();
            if (reserved != null) {
                for (String p : reserved) {
                    String v = trimToNull(p);
                    if (v == null) {
                        continue;
                    }
                    if (!isValidParamPattern(v)) {
                        errors.add("app.redirect.query-forward-reserved-params 包含不合法项: " + v);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            errors.add("redirect 配置校验异常: " + e.getMessage());
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

        // Edge 风控配置校验：仅校验“格式与边界”，不强制要求启用
        try {
            if (properties.getEdge() != null) {
                CidrBlocks.parseList(properties.getEdge().getTrustedProxies(), "app.edge.trusted-proxies");

                var rc = properties.getEdge().getRiskControl();
                if (rc != null) {
                    CidrBlocks.parseList(rc.getIpAllowlist(), "app.edge.risk-control.ip-allowlist");
                    CidrBlocks.parseList(rc.getIpDenylist(), "app.edge.risk-control.ip-denylist");

                    var rl = rc.getRateLimit();
                    if (rl != null && rl.isEnabled()) {
                        if (rl.getWindowSeconds() <= 0) {
                            errors.add("app.edge.risk-control.rate-limit.window-seconds 必须 > 0");
                        }
                        if (rl.getIpMaxRequests() < 0) {
                            errors.add("app.edge.risk-control.rate-limit.ip-max-requests 必须 >= 0");
                        }
                        if (rl.getIpCodeMaxRequests() < 0) {
                            errors.add("app.edge.risk-control.rate-limit.ip-code-max-requests 必须 >= 0");
                        }
                    }

                    var bot = rc.getBot();
                    if (bot != null && bot.isEnabled()) {
                        if (bot.getIpMaxRequests() < 0) {
                            errors.add("app.edge.risk-control.bot.ip-max-requests 必须 >= 0");
                        }
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
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

    private static boolean isHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            var u = java.net.URI.create(url.trim());
            String scheme = u.getScheme();
            return scheme != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (Exception e) {
            return false;
        }
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
