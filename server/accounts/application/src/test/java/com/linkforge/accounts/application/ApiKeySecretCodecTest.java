package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.foundation.config.SecurityProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeySecretCodecTest {

    @Test
    void hmacEncoding_shouldVerifyWithoutInvokingSlowPasswordHasher() {
        AccountsPasswordHasher legacyHasher = mock(AccountsPasswordHasher.class);
        SecurityProperties properties = new SecurityProperties();
        properties.getApiKey().setHmacPepper("independent-test-pepper-at-least-32-bytes");
        ApiKeySecretCodec codec = new ApiKeySecretCodec(legacyHasher, properties);

        String encoded = codec.encode("random-256-bit-api-key-secret");

        assertThat(encoded).startsWith(ApiKeySecretCodec.HMAC_SHA256_PREFIX);
        assertThat(codec.matches("random-256-bit-api-key-secret", encoded)).isTrue();
        assertThat(codec.matches("wrong-secret", encoded)).isFalse();
        assertThat(codec.needsUpgrade(encoded)).isFalse();
        verify(legacyHasher, never()).encode(org.mockito.ArgumentMatchers.anyString());
        verify(legacyHasher, never()).matches(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void legacyEncoding_shouldRemainVerifiableAndRequestUpgradeWhenPepperExists() {
        AccountsPasswordHasher legacyHasher = mock(AccountsPasswordHasher.class);
        when(legacyHasher.matches("secret", "$2a$legacy")).thenReturn(true);
        SecurityProperties properties = new SecurityProperties();
        properties.getApiKey().setHmacPepper("independent-test-pepper-at-least-32-bytes");
        ApiKeySecretCodec codec = new ApiKeySecretCodec(legacyHasher, properties);

        assertThat(codec.matches("secret", "$2a$legacy")).isTrue();
        assertThat(codec.needsUpgrade("$2a$legacy")).isTrue();
    }

    @Test
    void keyring_shouldVerifyCurrentPreviousAndLegacy_withoutAcceptingRemovedKeyIds() {
        AccountsPasswordHasher legacyHasher = mock(AccountsPasswordHasher.class);

        SecurityProperties oldProperties = new SecurityProperties();
        oldProperties.getApiKey().setCurrentKeyId("pepper-v1");
        oldProperties.getApiKey().setCurrentPepper("previous-pepper-at-least-32-bytes");
        ApiKeySecretCodec oldCodec = new ApiKeySecretCodec(legacyHasher, oldProperties);
        ApiKeySecretCodec.EncodedSecret oldEncoded = oldCodec.encodeCurrent("secret");

        SecurityProperties rotatingProperties = new SecurityProperties();
        rotatingProperties.getApiKey().setCurrentKeyId("pepper-v2");
        rotatingProperties.getApiKey().setCurrentPepper("current-pepper-at-least-32-bytes");
        rotatingProperties.getApiKey().setPreviousKeyId("pepper-v1");
        rotatingProperties.getApiKey().setPreviousPepper("previous-pepper-at-least-32-bytes");
        rotatingProperties.getApiKey().setLegacyPepper("legacy-pepper-at-least-32-bytes");
        ApiKeySecretCodec rotatingCodec = new ApiKeySecretCodec(legacyHasher, rotatingProperties);

        assertThat(rotatingCodec.matches("secret", oldEncoded.hash(), oldEncoded.keyId())).isTrue();
        assertThat(rotatingCodec.encodeCurrent("secret").keyId()).isEqualTo("pepper-v2");

        SecurityProperties completedProperties = new SecurityProperties();
        completedProperties.getApiKey().setCurrentKeyId("pepper-v2");
        completedProperties.getApiKey().setCurrentPepper("current-pepper-at-least-32-bytes");
        ApiKeySecretCodec completedCodec = new ApiKeySecretCodec(legacyHasher, completedProperties);

        assertThat(completedCodec.matches("secret", oldEncoded.hash(), oldEncoded.keyId())).isFalse();
    }

    @Test
    void singlePepperBridge_shouldPreserveDigestForNullAndVersionedRows() {
        AccountsPasswordHasher legacyHasher = mock(AccountsPasswordHasher.class);
        SecurityProperties properties = new SecurityProperties();
        properties.getApiKey().setCurrentKeyId("v1");
        properties.getApiKey().setHmacPepper("shared-rolling-pepper-at-least-32-bytes");
        properties.getApiKey().setLegacyJwtFallbackEnabled(false);
        ApiKeySecretCodec codec = new ApiKeySecretCodec(legacyHasher, properties);
        String existingHash = ApiKeySecretCodec.HMAC_SHA256_PREFIX
                + "Xhv2PG3awN66Bs9LclLoz_ymDSMEHTCDz6stdJGsSKs";

        assertThat(codec.matches("rolling-upgrade-secret", existingHash, null)).isTrue();

        ApiKeySecretCodec.EncodedSecret versioned = codec.encodeCurrent("rolling-upgrade-secret");
        assertThat(versioned.keyId()).isEqualTo("v1");
        assertThat(versioned.hash()).isEqualTo(existingHash);
        assertThat(codec.matches("rolling-upgrade-secret", versioned.hash(), versioned.keyId())).isTrue();
        verify(legacyHasher, never()).encode(org.mockito.ArgumentMatchers.anyString());
        verify(legacyHasher, never()).matches(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
