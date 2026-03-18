package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountsApiKeyStore;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.contract.openapi.OpenApiErrorCode;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.security.TenantGuard;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiKeyServiceTest {

    @Test
    void constructor_shouldDependOnApiKeyPortInsteadOfInfrastructureMapper() {
        Constructor<?> constructor = ApiKeyService.class.getDeclaredConstructors()[0];

        assertThat(Arrays.stream(constructor.getParameterTypes()).map(Class::getName))
                .contains("com.linkforge.accounts.application.port.AccountsApiKeyStore")
                .doesNotContain("com.linkforge.accounts.infrastructure.persistence.mapper.ApiKeyMapper")
                .doesNotContain("com.linkforge.accounts.infrastructure.persistence.repo.ApiKeyRepository");
    }

    @Test
    void authenticate_shouldUseRedisAuthCache_andSkipDbAndBcrypt_onCacheHit() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(60);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);

        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                store,
                encoder,
                mock(TenantGuard.class),
                props,
                redis
        );

        String digest = sha256Base64Url("secret");
        when(valueOps.get("auth:api_key:123")).thenReturn("v1|1|active|" + digest);

        ApiKeyService.ApiKeyAuthResult r = service.authenticate("lfk_123_secret");
        assertThat(r.tenantId()).isEqualTo(1L);
        assertThat(r.apiKeyId()).isEqualTo(123L);

        verifyNoInteractions(store);
        verifyNoInteractions(encoder);
    }

    @Test
    void authenticate_shouldReject_whenCacheHitButDigestMismatch() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(60);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);

        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                store,
                encoder,
                mock(TenantGuard.class),
                props,
                redis
        );

        String wrongDigest = sha256Base64Url("other-secret");
        when(valueOps.get("auth:api_key:123")).thenReturn("v1|1|active|" + wrongDigest);

        assertThatThrownBy(() -> service.authenticate("lfk_123_secret"))
                .isInstanceOf(ApiKeyService.ApiKeyAuthException.class)
                .extracting(ex -> ((ApiKeyService.ApiKeyAuthException) ex).errorCode())
                .isEqualTo(OpenApiErrorCode.API_KEY_INVALID);

        verifyNoInteractions(store);
        verifyNoInteractions(encoder);
    }

    @Test
    void authenticate_shouldRejectDisabled_whenCacheHitAndStatusDisabled() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(60);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);

        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                store,
                encoder,
                mock(TenantGuard.class),
                props,
                redis
        );

        when(valueOps.get("auth:api_key:123")).thenReturn("v1|1|disabled|");

        assertThatThrownBy(() -> service.authenticate("lfk_123_secret"))
                .isInstanceOf(ApiKeyService.ApiKeyAuthException.class)
                .extracting(ex -> ((ApiKeyService.ApiKeyAuthException) ex).errorCode())
                .isEqualTo(OpenApiErrorCode.API_KEY_DISABLED);

        verifyNoInteractions(store);
        verifyNoInteractions(encoder);
    }

    @Test
    void authenticate_shouldFallbackToDb_andBackfillCache_onCacheMiss() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);
        props.getApiKey().setAuthCacheTtlSeconds(60);

        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                store,
                encoder,
                mock(TenantGuard.class),
                props,
                redis
        );

        AccountsApiKeyStore.ApiKey apiKey = new AccountsApiKeyStore.ApiKey(
                123L,
                1L,
                "test-key",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );

        when(valueOps.get("auth:api_key:123")).thenReturn(null);
        when(store.findById(123L)).thenReturn(apiKey);
        when(encoder.matches("secret", "hash")).thenReturn(true);

        ApiKeyService.ApiKeyAuthResult r = service.authenticate("lfk_123_secret");
        assertThat(r.tenantId()).isEqualTo(1L);
        assertThat(r.apiKeyId()).isEqualTo(123L);

        verify(valueOps).set(eq("auth:api_key:123"), anyString(), any(Duration.class));
    }

    @Test
    void authenticate_shouldUpdateLastUsedAtOncePerWindow_usingRedisToken() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(60);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(300);

        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                store,
                encoder,
                mock(TenantGuard.class),
                props,
                redis
        );

        String digest = sha256Base64Url("secret");
        when(valueOps.get("auth:api_key:123")).thenReturn("v1|1|active|" + digest);
        when(valueOps.setIfAbsent(eq("auth:api_key:last_used:123"), eq("1"), eq(Duration.ofSeconds(300))))
                .thenReturn(true);

        service.authenticate("lfk_123_secret");

        verify(store).updateLastUsedAt(eq(123L), any());
    }

    @Test
    void authenticate_shouldReject_whenApiKeyHeaderIsTooLong() {
        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                mock(AccountsApiKeyStore.class),
                mock(PasswordEncoder.class),
                mock(TenantGuard.class),
                new SecurityProperties(),
                null
        );

        String longKey = "lfk_123_" + "a".repeat(512);
        assertThatThrownBy(() -> service.authenticate(longKey))
                .isInstanceOf(ApiKeyService.ApiKeyAuthException.class)
                .extracting(ex -> ((ApiKeyService.ApiKeyAuthException) ex).errorCode())
                .isEqualTo(OpenApiErrorCode.API_KEY_INVALID);
    }

    @Test
    void authenticate_shouldReject_whenApiKeySecretIsTooLong() {
        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                mock(AccountsApiKeyStore.class),
                mock(PasswordEncoder.class),
                mock(TenantGuard.class),
                new SecurityProperties(),
                null
        );

        String keyWithHugeSecret = "lfk_123_" + "a".repeat(129);
        assertThatThrownBy(() -> service.authenticate(keyWithHugeSecret))
                .isInstanceOf(ApiKeyService.ApiKeyAuthException.class)
                .extracting(ex -> ((ApiKeyService.ApiKeyAuthException) ex).errorCode())
                .isEqualTo(OpenApiErrorCode.API_KEY_INVALID);
    }

    @Test
    void disable_shouldReturnDisabledStatusInApiKeyInfo() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                store,
                mock(PasswordEncoder.class),
                mock(TenantGuard.class),
                new SecurityProperties(),
                null
        );

        AccountsApiKeyStore.ApiKey existing = new AccountsApiKeyStore.ApiKey(
                123L,
                1L,
                "test-key",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );
        when(store.findById(123L)).thenReturn(existing);

        ApiKeyService.ApiKeyInfo info = service.disable(1L, 123L);

        assertThat(info.status()).isEqualTo(AccountsConstants.STATUS_DISABLED);
        verify(store).update(argThat(apiKey -> AccountsConstants.STATUS_DISABLED.equals(apiKey.status())));
    }

    @Test
    void enable_shouldReturnActiveStatusInApiKeyInfo() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                store,
                mock(PasswordEncoder.class),
                mock(TenantGuard.class),
                new SecurityProperties(),
                null
        );

        AccountsApiKeyStore.ApiKey existing = new AccountsApiKeyStore.ApiKey(
                123L,
                1L,
                "test-key",
                "hash",
                AccountsConstants.STATUS_DISABLED,
                null,
                null
        );
        when(store.findById(123L)).thenReturn(existing);

        ApiKeyService.ApiKeyInfo info = service.enable(1L, 123L);

        assertThat(info.status()).isEqualTo(AccountsConstants.STATUS_ACTIVE);
        verify(store).update(argThat(apiKey -> AccountsConstants.STATUS_ACTIVE.equals(apiKey.status())));
    }

    private static String sha256Base64Url(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
