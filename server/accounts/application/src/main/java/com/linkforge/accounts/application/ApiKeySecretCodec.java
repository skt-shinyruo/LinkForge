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
    private final PepperKey current;
    private final PepperKey previous;
    private final byte[] legacyPepper;

    ApiKeySecretCodec(AccountsPasswordHasher legacyPasswordHasher, SecurityProperties securityProperties) {
        this.legacyPasswordHasher = Objects.requireNonNull(legacyPasswordHasher, "legacyPasswordHasher");
        SecurityProperties.ApiKey apiKey = securityProperties == null ? null : securityProperties.getApiKey();
        String compatibilityPepper = apiKey == null ? null : trimToNull(apiKey.getHmacPepper());
        boolean allowJwtFallback = apiKey == null || apiKey.isLegacyJwtFallbackEnabled();
        String jwtPepper = allowJwtFallback && securityProperties != null && securityProperties.getJwt() != null
                ? trimToNull(securityProperties.getJwt().getSecret())
                : null;
        String currentPepper = firstNonNull(
                apiKey == null ? null : trimToNull(apiKey.getCurrentPepper()),
                compatibilityPepper,
                jwtPepper
        );
        String currentKeyId = apiKey == null ? null : trimToNull(apiKey.getCurrentKeyId());
        this.current = currentPepper == null
                ? null
                : new PepperKey(currentKeyId == null ? "default" : currentKeyId, bytes(currentPepper));

        String previousKeyId = apiKey == null ? null : trimToNull(apiKey.getPreviousKeyId());
        String previousPepper = apiKey == null ? null : trimToNull(apiKey.getPreviousPepper());
        this.previous = previousKeyId == null || previousPepper == null
                ? null
                : new PepperKey(previousKeyId, bytes(previousPepper));

        String configuredLegacy = apiKey == null ? null : trimToNull(apiKey.getLegacyPepper());
        String legacy = firstNonNull(configuredLegacy, compatibilityPepper, jwtPepper);
        this.legacyPepper = legacy == null ? new byte[0] : bytes(legacy);
    }

    String encode(String secret) {
        return encodeCurrent(secret).hash();
    }

    EncodedSecret encodeCurrent(String secret) {
        Objects.requireNonNull(secret, "secret");
        if (current == null) {
            return new EncodedSecret(null, legacyPasswordHasher.encode(secret));
        }
        return new EncodedSecret(
                current.id(),
                HMAC_SHA256_PREFIX + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(hmac(secret, current.pepper()))
        );
    }

    boolean matches(String secret, String encoded) {
        return matches(secret, encoded, null);
    }

    boolean matches(String secret, String encoded, String keyId) {
        if (secret == null || encoded == null) {
            return false;
        }
        if (!encoded.startsWith(HMAC_SHA256_PREFIX)) {
            return legacyPasswordHasher.matches(secret, encoded);
        }
        byte[] selectedPepper = selectPepper(keyId);
        if (selectedPepper.length == 0) {
            return false;
        }
        try {
            byte[] expected = Base64.getUrlDecoder().decode(encoded.substring(HMAC_SHA256_PREFIX.length()));
            return MessageDigest.isEqual(hmac(secret, selectedPepper), expected);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    boolean needsUpgrade(String encoded) {
        return current != null && encoded != null && !encoded.startsWith(HMAC_SHA256_PREFIX);
    }

    boolean needsUpgrade(String encoded, String keyId) {
        return current != null
                && encoded != null
                && (!encoded.startsWith(HMAC_SHA256_PREFIX) || !current.id().equals(trimToNull(keyId)));
    }

    private byte[] selectPepper(String keyId) {
        String normalizedKeyId = trimToNull(keyId);
        if (normalizedKeyId == null) {
            return legacyPepper;
        }
        if (current != null && current.id().equals(normalizedKeyId)) {
            return current.pepper();
        }
        if (previous != null && previous.id().equals(normalizedKeyId)) {
            return previous.pepper();
        }
        return new byte[0];
    }

    private static byte[] hmac(String secret, byte[] pepper) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(pepper, HMAC_SHA256));
            return mac.doFinal(secret.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is unavailable", ex);
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    record EncodedSecret(String keyId, String hash) {
    }

    private record PepperKey(String id, byte[] pepper) {
    }
}
