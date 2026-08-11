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
}
