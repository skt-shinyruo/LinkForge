package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.foundation.config.SecurityProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;

/**
 * API Key secret 的版本化摘要编码器。
 *
 * <p>API Key secret 由 256 bit CSPRNG 生成，不需要密码型慢哈希抵抗字典攻击。稳定格式使用服务端
 * pepper 的 HMAC-SHA256，使每次认证只做固定成本摘要；历史 BCrypt 记录仍可验证，并在成功认证后升级。</p>
 */
final class ApiKeySecretCodec {

    static final String HMAC_SHA256_PREFIX = "{hmac-sha256}";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final AccountsPasswordHasher legacyPasswordHasher;
    private final byte[] pepper;

    ApiKeySecretCodec(AccountsPasswordHasher legacyPasswordHasher, SecurityProperties securityProperties) {
        this.legacyPasswordHasher = Objects.requireNonNull(legacyPasswordHasher, "legacyPasswordHasher");
        this.pepper = resolvePepper(securityProperties);
    }

    String encode(String secret) {
        Objects.requireNonNull(secret, "secret");
        if (pepper.length == 0) {
            return legacyPasswordHasher.encode(secret);
        }
        return HMAC_SHA256_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(secret));
    }

    boolean matches(String secret, String encoded) {
        if (secret == null || encoded == null) {
            return false;
        }
        if (!encoded.startsWith(HMAC_SHA256_PREFIX)) {
            return legacyPasswordHasher.matches(secret, encoded);
        }
        if (pepper.length == 0) {
            return false;
        }
        try {
            byte[] expected = Base64.getUrlDecoder().decode(encoded.substring(HMAC_SHA256_PREFIX.length()));
            return MessageDigest.isEqual(hmac(secret), expected);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    boolean needsUpgrade(String encoded) {
        return pepper.length > 0 && encoded != null && !encoded.startsWith(HMAC_SHA256_PREFIX);
    }

    private byte[] hmac(String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(pepper, HMAC_SHA256));
            return mac.doFinal(secret.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is unavailable", ex);
        }
    }

    private static byte[] resolvePepper(SecurityProperties properties) {
        String configured = null;
        if (properties != null && properties.getApiKey() != null) {
            configured = trimToNull(properties.getApiKey().getHmacPepper());
        }
        if (configured == null && properties != null && properties.getJwt() != null) {
            configured = trimToNull(properties.getJwt().getSecret());
        }
        return configured == null ? new byte[0] : configured.getBytes(StandardCharsets.UTF_8);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
