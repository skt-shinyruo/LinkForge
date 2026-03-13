package com.linkforge.accounts.application;

import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.accounts.infrastructure.persistence.entity.ApiKeyEntity;
import com.linkforge.accounts.infrastructure.persistence.mapper.ApiKeyMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiKeyServiceTest {

    @Test
    void constructor_shouldDependOnApiKeyMapperInsteadOfSpringDataRepository() {
        Constructor<?> constructor = ApiKeyService.class.getDeclaredConstructors()[0];

        assertThat(Arrays.stream(constructor.getParameterTypes()).map(Class::getName))
                .contains("com.linkforge.accounts.infrastructure.persistence.mapper.ApiKeyMapper")
                .doesNotContain("com.linkforge.accounts.infrastructure.persistence.repo.ApiKeyRepository");
    }

    @Test
    void authenticate_shouldUseRedisAuthCache_andSkipDbAndBcrypt_onCacheHit() {
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
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
                mapper,
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

        verifyNoInteractions(mapper);
        verifyNoInteractions(encoder);
    }

    @Test
    void authenticate_shouldReject_whenCacheHitButDigestMismatch() {
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
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
                mapper,
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

        verifyNoInteractions(mapper);
        verifyNoInteractions(encoder);
    }

    @Test
    void authenticate_shouldRejectDisabled_whenCacheHitAndStatusDisabled() {
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
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
                mapper,
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

        verifyNoInteractions(mapper);
        verifyNoInteractions(encoder);
    }

    @Test
    void authenticate_shouldFallbackToDb_andBackfillCache_onCacheMiss() {
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
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
                mapper,
                encoder,
                mock(TenantGuard.class),
                props,
                redis
        );

        ApiKeyEntity e = new ApiKeyEntity();
        e.setId(123L);
        e.setTenantId(1L);
        e.setStatus(AccountsConstants.STATUS_ACTIVE);
        e.setKeyHash("hash");
        e.setLastUsedAt(null);

        when(valueOps.get("auth:api_key:123")).thenReturn(null);
        when(mapper.findById(123L)).thenReturn(e);
        when(encoder.matches("secret", "hash")).thenReturn(true);

        ApiKeyService.ApiKeyAuthResult r = service.authenticate("lfk_123_secret");
        assertThat(r.tenantId()).isEqualTo(1L);
        assertThat(r.apiKeyId()).isEqualTo(123L);

        verify(valueOps).set(eq("auth:api_key:123"), anyString(), any(Duration.class));
    }

    @Test
    void authenticate_shouldUpdateLastUsedAtOncePerWindow_usingRedisToken() {
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
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
                mapper,
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

        verify(mapper).updateLastUsedAt(eq(123L), any());
    }

    @Test
    void authenticate_shouldReject_whenApiKeyHeaderIsTooLong() {
        ApiKeyService service = new ApiKeyService(
                mock(SnowflakeIdGenerator.class),
                mock(ApiKeyMapper.class),
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
                mock(ApiKeyMapper.class),
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
