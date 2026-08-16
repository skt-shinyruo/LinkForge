package com.linkforge.accounts.interfaces.startup;

import com.linkforge.foundation.config.CorsProperties;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.config.StartupValidation;
import com.linkforge.foundation.runtime.startup.StartupCheck;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Accounts 上下文的启动安全门禁。
 *
 * <p>该检查由应用启动器统一收集错误后一次性中止启动，不在此处直接抛异常。无论是否为严格模式，
 * JWT 密钥缺失、API Key key id 超过持久化字段上限、Cookie 属性组合非法，以及携带凭证的 CORS 配置使用
 * 通配来源都会被拒绝；
 * {@code prod} profile 或 {@code app.strict-config=true} 启用严格模式后，还会拒绝开发示例密钥和
 * 未启用 Secure 的认证 Cookie，防止可启动但不适合生产的配置进入服务流量。</p>
 *
 * <p>这里只校验能够静态判断的配置关系，不替代请求期的 JWT、账户状态、租户状态和 CSRF 校验。</p>
 */
@Component
public class AccountsStartupCheck implements StartupCheck {

    private static final int MAX_API_KEY_ID_LENGTH = 64;
    private static final Set<String> SAME_SITE = Set.of("Lax", "Strict", "None");

    private final SecurityProperties securityProperties;
    private final CorsProperties corsProperties;

    public AccountsStartupCheck(SecurityProperties securityProperties, CorsProperties corsProperties) {
        this.securityProperties = securityProperties;
        this.corsProperties = corsProperties;
    }

    /**
     * 将当前配置的全部错误追加到共享错误列表。
     *
     * @param strict 是否按生产安全基线校验
     * @param errors 启动器提供的聚合错误列表
     */
    @Override
    public void validate(boolean strict, List<String> errors) {
        validateJwt(strict, errors);
        validateApiKey(strict, errors);
        validateCors(errors);
    }

    private void validateApiKey(boolean strict, List<String> errors) {
        SecurityProperties.ApiKey apiKey = securityProperties == null ? null : securityProperties.getApiKey();
        if (apiKey == null) {
            if (strict) {
                errors.add("生产环境必须配置 app.security.api-key.current-pepper");
            }
            return;
        }

        String currentKeyId = StartupValidation.trimToNull(apiKey.getCurrentKeyId());
        String currentPepper = StartupValidation.trimToNull(apiKey.getCurrentPepper());
        String previousKeyId = StartupValidation.trimToNull(apiKey.getPreviousKeyId());
        String previousPepper = StartupValidation.trimToNull(apiKey.getPreviousPepper());
        if (currentKeyId != null && currentKeyId.length() > MAX_API_KEY_ID_LENGTH) {
            errors.add("app.security.api-key.current-key-id 长度不能超过 64");
        }
        if (previousKeyId != null && previousKeyId.length() > MAX_API_KEY_ID_LENGTH) {
            errors.add("app.security.api-key.previous-key-id 长度不能超过 64");
        }
        if ((previousKeyId == null) != (previousPepper == null)) {
            errors.add("app.security.api-key.previous-key-id 与 previous-pepper 必须成对配置");
        }
        if (currentKeyId != null && currentKeyId.equals(previousKeyId)) {
            errors.add("API Key current/previous key id 不能相同");
        }
        if (!strict) {
            return;
        }

        if (currentKeyId == null) {
            errors.add("生产环境必须配置 app.security.api-key.current-key-id");
        }
        if (currentPepper == null) {
            errors.add("生产环境必须配置 app.security.api-key.current-pepper");
        }
        if (apiKey.isLegacyJwtFallbackEnabled()) {
            errors.add("生产环境必须关闭 API Key JWT fallback");
        }
        String jwtSecret = securityProperties.getJwt() == null
                ? null
                : StartupValidation.trimToNull(securityProperties.getJwt().getSecret());
        if (currentPepper != null && currentPepper.equals(jwtSecret)) {
            errors.add("API Key current pepper 必须独立于 JWT secret");
        }
        if (previousPepper != null && previousPepper.equals(jwtSecret)) {
            errors.add("API Key previous pepper 必须独立于 JWT secret");
        }
        String legacyPepper = StartupValidation.trimToNull(apiKey.getLegacyPepper());
        if (legacyPepper != null && legacyPepper.equals(jwtSecret)) {
            errors.add("API Key legacy pepper 必须独立于 JWT secret");
        }
        String compatibilityPepper = StartupValidation.trimToNull(apiKey.getHmacPepper());
        if (compatibilityPepper != null && compatibilityPepper.equals(jwtSecret)) {
            errors.add("API Key hmac-pepper 必须独立于 JWT secret");
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
