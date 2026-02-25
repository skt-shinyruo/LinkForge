package com.linkforge.api;

import com.linkforge.platform.config.AppProperties;
import com.linkforge.platform.config.StartupValidation;
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

        StartupValidation.validateIdBasics(properties, strict, log, errors);

        if (StartupValidation.isBlank(properties.getBaseUrl())) {
            errors.add("app.base-url 不能为空（用于拼接 shortUrl）");
        }

        // redirect/cache 配置校验（API 服务也会写入 LinkMeta 缓存，需保证 TTL 合法）
        try {
            StartupValidation.validateRedirectBasics(properties, errors);
        } catch (Exception e) {
            errors.add("redirect 配置读取失败: " + e.getMessage());
        }

        String jwtSecret = properties.getSecurity().getJwt().getSecret();
        if (StartupValidation.isBlank(jwtSecret)) {
            errors.add("app.security.jwt.secret 不能为空（用于签发/校验 JWT）");
        } else if (StartupValidation.looksLikeDev(jwtSecret)) {
            if (strict) {
                errors.add("JWT secret 看起来像开发默认值，请在生产环境覆盖 JWT_SECRET");
            } else {
                log.warn("JWT secret 使用了疑似开发默认值；生产环境请覆盖 JWT_SECRET");
            }
        }

        StartupValidation.validateAnalyticsBasics(properties, strict, log, errors);

        // analytics 追踪参数采集白名单（可选）：仅做格式校验，避免误配把敏感 query 带入统计
        try {
            StartupValidation.validateAnalyticsTrackingAllowlist(properties, errors);
            StartupValidation.validateAnalyticsDimensionsTypes(properties, errors);
            StartupValidation.validateAnalyticsEvents(properties, errors);
        } catch (Exception e) {
            errors.add("analytics 配置校验异常: " + e.getMessage());
        }

        if (properties.getSecurity().getJwt().isCookieEnabled()) {
            String cookieName = properties.getSecurity().getJwt().getCookieName();
            if (StartupValidation.isBlank(cookieName)) {
                errors.add("cookie 模式已开启，但 app.security.jwt.cookie-name 为空");
            }
            String sameSite = properties.getSecurity().getJwt().getCookieSameSite();
            if (!StartupValidation.isBlank(sameSite) && !SAME_SITE.contains(sameSite.trim())) {
                errors.add("app.security.jwt.cookie-same-site 仅支持 Lax/Strict/None");
            }
            if ("None".equalsIgnoreCase(StartupValidation.trimToNull(sameSite))
                    && !properties.getSecurity().getJwt().isCookieSecure()) {
                // 浏览器要求 SameSite=None 必须 Secure，否则可能导致 cookie 被丢弃；同时也属于安全风险
                errors.add("cookie-same-site=None 时必须启用 app.security.jwt.cookie-secure=true");
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

    private static String trimToNull(String v) {
        return StartupValidation.trimToNull(v);
    }

    private static boolean isValidParamPattern(String p) {
        return StartupValidation.isValidParamPattern(p);
    }
}
