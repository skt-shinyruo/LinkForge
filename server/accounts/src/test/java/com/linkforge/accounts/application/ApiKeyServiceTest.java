package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountsApiKeyStore;
import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.accounts.application.port.ApiKeyAuthCache;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.contract.openapi.OpenApiErrorCode;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.runtime.security.TenantGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiKeyServiceTest {

    private TimeZone originalTimeZone;

    @BeforeEach
    void setUp() {
        originalTimeZone = TimeZone.getDefault();
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    void constructor_shouldDependOnApiKeyPortInsteadOfInfrastructureMapper() {
        Constructor<?> constructor = ApiKeyService.class.getDeclaredConstructors()[0];

        assertThat(Arrays.stream(constructor.getParameterTypes()).map(Class::getName))
                .contains("com.linkforge.accounts.application.port.AccountsApiKeyStore")
                .doesNotContain("com.linkforge.accounts.infrastructure.persistence.mapper.ApiKeyMapper")
                .doesNotContain("com.linkforge.accounts.infrastructure.persistence.repo.ApiKeyRepository");
    }

    @Test
    void constructor_shouldDependOnApplicationPorts_insteadOfSpringRedisOrPasswordEncoder() {
        Constructor<?> constructor = ApiKeyService.class.getDeclaredConstructors()[0];

        assertThat(Arrays.stream(constructor.getParameterTypes()).map(Class::getName))
                .contains(
                        "com.linkforge.accounts.application.port.AccountsPasswordHasher",
                        "com.linkforge.accounts.application.port.ApiKeyAuthCache"
                )
                .doesNotContain("org.springframework.data.redis.core.StringRedisTemplate")
                .doesNotContain("org.springframework.security.crypto.password.PasswordEncoder");
    }

    @Test
    void authenticate_shouldUseRedisAuthCache_andSkipDbAndBcrypt_onCacheHit() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        ApiKeyAuthCache authCache = mock(ApiKeyAuthCache.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(60);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);

        ApiKeyService service = newService(store, passwordHasher, props, authCache);

        String digest = sha256Base64Url("secret");
        when(authCache.read(123L)).thenReturn(new ApiKeyAuthCache.Entry(1L, AccountsConstants.STATUS_ACTIVE, digest));

        ApiKeyService.ApiKeyAuthResult r = service.authenticate("lfk_123_secret");
        assertThat(r.tenantId()).isEqualTo(1L);
        assertThat(r.apiKeyId()).isEqualTo(123L);

        verifyNoInteractions(store);
        verifyNoInteractions(passwordHasher);
    }

    @Test
    void authenticate_shouldReject_whenCacheHitButDigestMismatch() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        ApiKeyAuthCache authCache = mock(ApiKeyAuthCache.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(60);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);

        ApiKeyService service = newService(store, passwordHasher, props, authCache);

        String wrongDigest = sha256Base64Url("other-secret");
        when(authCache.read(123L)).thenReturn(new ApiKeyAuthCache.Entry(1L, AccountsConstants.STATUS_ACTIVE, wrongDigest));

        assertThatThrownBy(() -> service.authenticate("lfk_123_secret"))
                .isInstanceOf(ApiKeyService.ApiKeyAuthException.class)
                .extracting(ex -> ((ApiKeyService.ApiKeyAuthException) ex).errorCode())
                .isEqualTo(OpenApiErrorCode.API_KEY_INVALID);

        verifyNoInteractions(store);
        verifyNoInteractions(passwordHasher);
    }

    @Test
    void authenticate_shouldRejectDisabled_whenCacheHitAndStatusDisabled() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        ApiKeyAuthCache authCache = mock(ApiKeyAuthCache.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(60);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);

        ApiKeyService service = newService(store, passwordHasher, props, authCache);

        when(authCache.read(123L)).thenReturn(new ApiKeyAuthCache.Entry(1L, AccountsConstants.STATUS_DISABLED, ""));

        assertThatThrownBy(() -> service.authenticate("lfk_123_secret"))
                .isInstanceOf(ApiKeyService.ApiKeyAuthException.class)
                .extracting(ex -> ((ApiKeyService.ApiKeyAuthException) ex).errorCode())
                .isEqualTo(OpenApiErrorCode.API_KEY_DISABLED);

        verifyNoInteractions(store);
        verifyNoInteractions(passwordHasher);
    }

    @Test
    void authenticate_shouldFallbackToDb_andBackfillCache_onCacheMiss() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        ApiKeyAuthCache authCache = mock(ApiKeyAuthCache.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);
        props.getApiKey().setAuthCacheTtlSeconds(60);

        ApiKeyService service = newService(store, passwordHasher, props, authCache);

        AccountsApiKeyStore.ApiKey apiKey = new AccountsApiKeyStore.ApiKey(
                123L,
                1L,
                "test-key",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );

        when(authCache.read(123L)).thenReturn(null);
        when(store.findById(123L)).thenReturn(apiKey);
        when(passwordHasher.matches("secret", "hash")).thenReturn(true);

        ApiKeyService.ApiKeyAuthResult r = service.authenticate("lfk_123_secret");
        assertThat(r.tenantId()).isEqualTo(1L);
        assertThat(r.apiKeyId()).isEqualTo(123L);

        verify(authCache).putActive(eq(123L), eq(1L), any(), eq(60L));
    }

    @Test
    void authenticate_shouldBypassAuthCacheRead_whenAuthCacheTtlIsDisabled() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        ApiKeyAuthCache authCache = mock(ApiKeyAuthCache.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(0);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);

        ApiKeyService service = newService(store, passwordHasher, props, authCache);

        AccountsApiKeyStore.ApiKey apiKey = new AccountsApiKeyStore.ApiKey(
                123L,
                1L,
                "test-key",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );

        when(store.findById(123L)).thenReturn(apiKey);
        when(passwordHasher.matches("secret", "hash")).thenReturn(true);

        ApiKeyService.ApiKeyAuthResult result = service.authenticate("lfk_123_secret");

        assertThat(result.tenantId()).isEqualTo(1L);
        assertThat(result.apiKeyId()).isEqualTo(123L);
        verifyNoInteractions(authCache);
    }

    @Test
    void authenticate_shouldUpdateLastUsedAtOncePerWindow_usingRedisToken() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        ApiKeyAuthCache authCache = mock(ApiKeyAuthCache.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(60);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(300);

        ApiKeyService service = newService(store, passwordHasher, props, authCache);

        String digest = sha256Base64Url("secret");
        when(authCache.read(123L)).thenReturn(new ApiKeyAuthCache.Entry(1L, AccountsConstants.STATUS_ACTIVE, digest));
        when(authCache.tryAcquireLastUsedToken(123L, 300L)).thenReturn(ApiKeyAuthCache.LastUsedTokenResult.ACQUIRED);

        service.authenticate("lfk_123_secret");

        verify(store).updateLastUsedAt(eq(123L), any());
    }

    @Test
    void authenticate_shouldWriteLastUsedAt_inUtcBasedOnInjectedClock() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        ApiKeyAuthCache authCache = mock(ApiKeyAuthCache.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(60);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(300);

        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-19T01:02:03Z"), ZoneOffset.UTC);
        ApiKeyService service = newService(store, passwordHasher, props, authCache, fixedClock);

        String digest = sha256Base64Url("secret");
        when(authCache.read(123L)).thenReturn(new ApiKeyAuthCache.Entry(1L, AccountsConstants.STATUS_ACTIVE, digest));
        when(authCache.tryAcquireLastUsedToken(123L, 300L)).thenReturn(ApiKeyAuthCache.LastUsedTokenResult.ACQUIRED);

        service.authenticate("lfk_123_secret");

        ArgumentCaptor<LocalDateTime> lastUsedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(store).updateLastUsedAt(eq(123L), lastUsedAt.capture());
        assertThat(lastUsedAt.getValue()).isEqualTo(LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC));
    }

    @Test
    void authenticate_shouldReject_whenApiKeyHeaderIsTooLong() {
        ApiKeyService service = newService(
                mock(AccountsApiKeyStore.class),
                mock(AccountsPasswordHasher.class),
                new SecurityProperties(),
                mock(ApiKeyAuthCache.class)
        );

        String longKey = "lfk_123_" + "a".repeat(512);
        assertThatThrownBy(() -> service.authenticate(longKey))
                .isInstanceOf(ApiKeyService.ApiKeyAuthException.class)
                .extracting(ex -> ((ApiKeyService.ApiKeyAuthException) ex).errorCode())
                .isEqualTo(OpenApiErrorCode.API_KEY_INVALID);
    }

    @Test
    void authenticate_shouldReject_whenApiKeySecretIsTooLong() {
        ApiKeyService service = newService(
                mock(AccountsApiKeyStore.class),
                mock(AccountsPasswordHasher.class),
                new SecurityProperties(),
                mock(ApiKeyAuthCache.class)
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
        ApiKeyService service = newService(
                store,
                mock(AccountsPasswordHasher.class),
                new SecurityProperties(),
                mock(ApiKeyAuthCache.class)
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
        ApiKeyService service = newService(
                store,
                mock(AccountsPasswordHasher.class),
                new SecurityProperties(),
                mock(ApiKeyAuthCache.class)
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

    private static ApiKeyService newService(
            AccountsApiKeyStore store,
            AccountsPasswordHasher passwordHasher,
            SecurityProperties props,
            ApiKeyAuthCache authCache
    ) {
        return newService(store, passwordHasher, props, authCache, Clock.systemUTC());
    }

    private static ApiKeyService newService(
            AccountsApiKeyStore store,
            AccountsPasswordHasher passwordHasher,
            SecurityProperties props,
            ApiKeyAuthCache authCache,
            Clock clock
    ) {
        try {
            Constructor<ApiKeyService> constructor = ApiKeyService.class.getConstructor(
                    SnowflakeIdGenerator.class,
                    AccountsApiKeyStore.class,
                    AccountsPasswordHasher.class,
                    TenantGuard.class,
                    SecurityProperties.class,
                    ApiKeyAuthCache.class,
                    Clock.class
            );
            return constructor.newInstance(
                    mock(SnowflakeIdGenerator.class),
                    store,
                    passwordHasher,
                    mock(TenantGuard.class),
                    props,
                    authCache,
                    clock
            );
        } catch (NoSuchMethodException ignored) {
            try {
                Constructor<ApiKeyService> constructor = ApiKeyService.class.getConstructor(
                        SnowflakeIdGenerator.class,
                        AccountsApiKeyStore.class,
                        AccountsPasswordHasher.class,
                        TenantGuard.class,
                        SecurityProperties.class,
                        ApiKeyAuthCache.class
                );
                return constructor.newInstance(
                        mock(SnowflakeIdGenerator.class),
                        store,
                        passwordHasher,
                        mock(TenantGuard.class),
                        props,
                        authCache
                );
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
