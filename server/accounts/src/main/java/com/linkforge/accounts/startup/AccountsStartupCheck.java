package com.linkforge.accounts.startup;

import com.linkforge.foundation.config.CorsProperties;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.config.StartupValidation;
import com.linkforge.foundation.runtime.startup.StartupCheck;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class AccountsStartupCheck implements StartupCheck {

    private static final Set<String> SAME_SITE = Set.of("Lax", "Strict", "None");

    private final SecurityProperties securityProperties;
    private final CorsProperties corsProperties;

    public AccountsStartupCheck(SecurityProperties securityProperties, CorsProperties corsProperties) {
        this.securityProperties = securityProperties;
        this.corsProperties = corsProperties;
    }

    @Override
    public void validate(boolean strict, List<String> errors) {
        validateJwt(strict, errors);
        validateCors(errors);
    }

    private void validateJwt(boolean strict, List<String> errors) {
        SecurityProperties.Jwt jwt = securityProperties == null ? null : securityProperties.getJwt();
        String jwtSecret = jwt == null ? null : jwt.getSecret();
        if (StartupValidation.isBlank(jwtSecret)) {
            errors.add("app.security.jwt.secret 不能为空（用于签发/校验 JWT）");
        } else if (StartupValidation.looksLikeDev(jwtSecret)) {
            if (strict) {
                errors.add("JWT secret 看起来像开发默认值，请在生产环境覆盖 JWT_SECRET");
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
            errors.add("cookie-same-site=None 时必须启用 app.security.jwt.cookie-secure=true");
        }

        if (strict && !jwt.isCookieSecure()) {
            errors.add("生产环境 cookie 模式建议开启 app.security.jwt.cookie-secure=true");
        }
    }

    private void validateCors(List<String> errors) {
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
}
