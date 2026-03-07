package com.linkforge.app.startup;

import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.foundation.config.CorsProperties;
import com.linkforge.foundation.config.EdgeProperties;
import com.linkforge.foundation.config.IdProperties;
import com.linkforge.foundation.config.RedirectProperties;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.config.StartupValidation;
import com.linkforge.redirect.domain.net.CidrBlocks;
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

/**
 * Bootstrap-layer startup validation.
 *
 * <p>Rationale: keep configuration guardrails in the executable {@code app} module,
 * instead of scattering them inside domain bounded contexts.</p>
 */
@Component
public class AppStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AppStartupValidator.class);

    private static final Set<String> SAME_SITE = Set.of("Lax", "Strict", "None");

    private final Environment env;
    private final CoreProperties coreProperties;
    private final IdProperties idProperties;
    private final SecurityProperties securityProperties;
    private final CorsProperties corsProperties;
    private final RedirectProperties redirectProperties;
    private final AnalyticsProperties analyticsProperties;
    private final EdgeProperties edgeProperties;

    public AppStartupValidator(
            Environment env,
            CoreProperties coreProperties,
            IdProperties idProperties,
            SecurityProperties securityProperties,
            CorsProperties corsProperties,
            RedirectProperties redirectProperties,
            AnalyticsProperties analyticsProperties,
            EdgeProperties edgeProperties
    ) {
        this.env = env;
        this.coreProperties = coreProperties;
        this.idProperties = idProperties;
        this.securityProperties = securityProperties;
        this.corsProperties = corsProperties;
        this.redirectProperties = redirectProperties;
        this.analyticsProperties = analyticsProperties;
        this.edgeProperties = edgeProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean strict = env.acceptsProfiles(Profiles.of("prod"))
                || env.getProperty("app.strict-config", Boolean.class, false);

        List<String> errors = new ArrayList<>();

        StartupValidation.validateIdBasics(idProperties, strict, log, errors);

        // API shortlink requires baseUrl for shortUrl composition.
        if (StartupValidation.isBlank(coreProperties == null ? null : coreProperties.getBaseUrl())) {
            errors.add("app.base-url 不能为空（用于拼接 shortUrl）");
        }

        validateJwt(strict, errors);
        validateCors(strict, errors);
        validateRedirect(errors);
        validateAnalytics(strict, errors);
        validateEdgeRiskControl(errors);

        if (!errors.isEmpty()) {
            throw new IllegalStateException("启动配置校验失败: " + String.join("; ", errors));
        }
    }

    private void validateJwt(boolean strict, List<String> errors) {
        SecurityProperties.Jwt jwt = securityProperties == null ? null : securityProperties.getJwt();
        String jwtSecret = jwt == null ? null : jwt.getSecret();
        if (StartupValidation.isBlank(jwtSecret)) {
            errors.add("app.security.jwt.secret 不能为空（用于签发/校验 JWT）");
        } else if (StartupValidation.looksLikeDev(jwtSecret)) {
            if (strict) {
                errors.add("JWT secret 看起来像开发默认值，请在生产环境覆盖 JWT_SECRET");
            } else {
                log.warn("JWT secret 使用了疑似开发默认值；生产环境请覆盖 JWT_SECRET");
            }
        }

        if (jwt == null || !jwt.isCookieEnabled()) {
            return;
        }

        String cookieName = jwt.getCookieName();
        if (StartupValidation.isBlank(cookieName)) {
            errors.add("cookie 模式已开启，但 app.security.jwt.cookie-name 为空");
        }

        String sameSite = jwt.getCookieSameSite();
        if (!StartupValidation.isBlank(sameSite) && !SAME_SITE.contains(sameSite.trim())) {
            errors.add("app.security.jwt.cookie-same-site 仅支持 Lax/Strict/None");
        }

        if ("None".equalsIgnoreCase(StartupValidation.trimToNull(sameSite))
                && !jwt.isCookieSecure()) {
            // Browsers require SameSite=None to be Secure, otherwise cookie might be dropped.
            errors.add("cookie-same-site=None 时必须启用 app.security.jwt.cookie-secure=true");
        }

        if (strict && !jwt.isCookieSecure()) {
            errors.add("生产环境 cookie 模式建议开启 app.security.jwt.cookie-secure=true");
        }
    }

    private void validateCors(boolean strict, List<String> errors) {
        // CORS: allowCredentials=true must use explicit origin allowlist, "*" is forbidden.
        if (corsProperties == null || !corsProperties.isAllowCredentials()) {
            return;
        }
        var origins = corsProperties.getAllowedOrigins();
        boolean hasWildcard = origins != null && origins.stream().anyMatch(o -> o != null && o.trim().equals("*"));
        boolean hasNonBlank = origins != null && origins.stream().anyMatch(o -> o != null && !o.trim().isBlank());
        if (!hasNonBlank) {
            errors.add("CORS allowCredentials=true 时必须配置 app.cors.allowed-origins 白名单");
        }
        if (hasWildcard) {
            errors.add("CORS allowCredentials=true 时禁止 allowed-origins 包含 \"*\"");
        }
    }

    private void validateRedirect(List<String> errors) {
        try {
            StartupValidation.validateRedirectBasics(redirectProperties, errors);
        } catch (Exception e) {
            errors.add("redirect 配置读取失败: " + e.getMessage());
            return;
        }

        // Redirect experience config (optional)
        try {
            String notFoundLandingUrl = trimToNull(redirectProperties == null ? null : redirectProperties.getNotFoundLandingUrl());
            if (notFoundLandingUrl != null && !isHttpUrl(notFoundLandingUrl)) {
                errors.add("app.redirect.not-found-landing-url 必须为 http/https URL");
            }
            String goneLandingUrl = trimToNull(redirectProperties == null ? null : redirectProperties.getGoneLandingUrl());
            if (goneLandingUrl != null && !isHttpUrl(goneLandingUrl)) {
                errors.add("app.redirect.gone-landing-url 必须为 http/https URL");
            }

            String mode = trimToNull(redirectProperties == null ? null : redirectProperties.getQueryForwardMode());
            if (mode != null) {
                String t = mode.trim().toUpperCase();
                if (!("OFF".equals(t) || "ALLOWLIST".equals(t) || "ALL".equals(t))) {
                    errors.add("app.redirect.query-forward-mode 仅支持 OFF/ALLOWLIST/ALL");
                }
            }

            var allowlist = redirectProperties == null ? null : redirectProperties.getQueryForwardAllowlist();
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

            var reserved = redirectProperties == null ? null : redirectProperties.getQueryForwardReservedParams();
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
    }

    private void validateAnalytics(boolean strict, List<String> errors) {
        StartupValidation.validateAnalyticsBasics(analyticsProperties, strict, log, errors);

        // Tracking allowlist / dim types / visit events config (optional)
        try {
            StartupValidation.validateAnalyticsTrackingAllowlist(analyticsProperties, errors);
            StartupValidation.validateAnalyticsDimensionsTypes(analyticsProperties, errors);
            StartupValidation.validateAnalyticsEvents(analyticsProperties, errors);
        } catch (Exception e) {
            errors.add("analytics 配置校验异常: " + e.getMessage());
        }
    }

    private void validateEdgeRiskControl(List<String> errors) {
        // Edge risk control guardrails: validate format and boundary only, not forcing enablement.
        try {
            if (edgeProperties != null) {
                CidrBlocks.parseList(edgeProperties.getTrustedProxies(), "app.edge.trusted-proxies");

                var rc = edgeProperties.getRiskControl();
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
        } catch (Exception e) {
            errors.add("edge 配置校验异常: " + e.getMessage());
        }
    }

    private static String trimToNull(String v) {
        return StartupValidation.trimToNull(v);
    }

    private static boolean isValidParamPattern(String p) {
        return StartupValidation.isValidParamPattern(p);
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
}
