package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountsApiKeyStore;
import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.accounts.application.port.ApiKeyAuthCache;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.contract.openapi.OpenApiErrorCode;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.tx.PostCommitHookPort;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
                        "com.linkforge.accounts.application.port.ApiKeyAuthCache",
                        "com.linkforge.contract.platform.ApplicationScopePort"
                )
                .doesNotContain("com.linkforge.foundation.runtime.security.TenantGuard")
                .doesNotContain("org.springframework.data.redis.core.StringRedisTemplate")
                .doesNotContain("org.springframework.security.crypto.password.PasswordEncoder")
                .doesNotContain("com.linkforge.platform.application.PlatformControlPlaneService");
    }

    @Test
    void authenticate_shouldVerifyDbAndBcryptBeforeAcceptingActiveCacheHit() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        ApiKeyAuthCache authCache = mock(ApiKeyAuthCache.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(60);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);

        ApiKeyService service = newService(store, passwordHasher, props, authCache);

        String digest = sha256Base64Url("secret");
        AccountsApiKeyStore.ApiKey apiKey = new AccountsApiKeyStore.ApiKey(
                123L,
                1L,
                null,
                "test-key",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );
        when(authCache.read(123L)).thenReturn(new ApiKeyAuthCache.Entry(1L, null, AccountsConstants.STATUS_ACTIVE, digest));
        when(store.findById(123L)).thenReturn(apiKey);
        when(passwordHasher.matches("secret", "hash")).thenReturn(true);

        ApiKeyAuthResult r = service.authenticate("lfk_123_secret");
        assertThat(r.tenantId()).isEqualTo(1L);
        assertThat(r.apiKeyId()).isEqualTo(123L);

        verify(store).findById(123L);
        verify(passwordHasher).matches("secret", "hash");
    }

    @Test
    void authenticate_shouldRejectDisabledDbRecord_whenActiveCacheIsStale() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        ApiKeyAuthCache authCache = mock(ApiKeyAuthCache.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(60);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);

        ApiKeyService service = newService(store, passwordHasher, props, authCache);

        String digest = sha256Base64Url("secret");
        AccountsApiKeyStore.ApiKey apiKey = new AccountsApiKeyStore.ApiKey(
                123L,
                1L,
                null,
                "test-key",
                "hash",
                AccountsConstants.STATUS_DISABLED,
                null,
                null
        );
        when(authCache.read(123L)).thenReturn(new ApiKeyAuthCache.Entry(1L, null, AccountsConstants.STATUS_ACTIVE, digest));
        when(store.findById(123L)).thenReturn(apiKey);

        assertThatThrownBy(() -> service.authenticate("lfk_123_secret"))
                .isInstanceOf(ApiKeyService.ApiKeyAuthException.class)
                .extracting(ex -> ((ApiKeyService.ApiKeyAuthException) ex).errorCode())
                .isEqualTo(OpenApiErrorCode.API_KEY_DISABLED);
    }

    @Test
    void authenticate_shouldRejectOldSecret_whenActiveCacheSurvivesRotation() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        ApiKeyAuthCache authCache = mock(ApiKeyAuthCache.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(60);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);

        ApiKeyService service = newService(store, passwordHasher, props, authCache);

        String oldDigest = sha256Base64Url("old-secret");
        AccountsApiKeyStore.ApiKey apiKey = new AccountsApiKeyStore.ApiKey(
                123L,
                1L,
                null,
                "test-key",
                "new-hash",
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );
        when(authCache.read(123L)).thenReturn(new ApiKeyAuthCache.Entry(1L, null, AccountsConstants.STATUS_ACTIVE, oldDigest));
        when(store.findById(123L)).thenReturn(apiKey);
        when(passwordHasher.matches("old-secret", "new-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate("lfk_123_old-secret"))
                .isInstanceOf(ApiKeyService.ApiKeyAuthException.class)
                .extracting(ex -> ((ApiKeyService.ApiKeyAuthException) ex).errorCode())
                .isEqualTo(OpenApiErrorCode.API_KEY_INVALID);
    }

    @Test
    void authenticate_shouldReject_whenDbHashDoesNotMatchEvenIfActiveCacheExists() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        ApiKeyAuthCache authCache = mock(ApiKeyAuthCache.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(60);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);

        ApiKeyService service = newService(store, passwordHasher, props, authCache);

        String wrongDigest = sha256Base64Url("other-secret");
        AccountsApiKeyStore.ApiKey apiKey = new AccountsApiKeyStore.ApiKey(
                123L,
                1L,
                null,
                "test-key",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );
        when(authCache.read(123L)).thenReturn(new ApiKeyAuthCache.Entry(1L, null, AccountsConstants.STATUS_ACTIVE, wrongDigest));
        when(store.findById(123L)).thenReturn(apiKey);
        when(passwordHasher.matches("secret", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate("lfk_123_secret"))
                .isInstanceOf(ApiKeyService.ApiKeyAuthException.class)
                .extracting(ex -> ((ApiKeyService.ApiKeyAuthException) ex).errorCode())
                .isEqualTo(OpenApiErrorCode.API_KEY_INVALID);

        verify(store).findById(123L);
        verify(passwordHasher).matches("secret", "hash");
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

        when(authCache.read(123L)).thenReturn(new ApiKeyAuthCache.Entry(1L, null, AccountsConstants.STATUS_DISABLED, ""));

        assertThatThrownBy(() -> service.authenticate("lfk_123_secret"))
                .isInstanceOf(ApiKeyService.ApiKeyAuthException.class)
                .extracting(ex -> ((ApiKeyService.ApiKeyAuthException) ex).errorCode())
                .isEqualTo(OpenApiErrorCode.API_KEY_DISABLED);

        verifyNoInteractions(store);
        verifyNoInteractions(passwordHasher);
    }

    @Test
    void authenticate_shouldFallbackToDb_withoutBackfillingActiveCache_onCacheMiss() {
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
                null,
                "test-key",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );

        when(authCache.read(123L)).thenReturn(null);
        when(store.findById(123L)).thenReturn(apiKey);
        when(passwordHasher.matches("secret", "hash")).thenReturn(true);

        ApiKeyAuthResult r = service.authenticate("lfk_123_secret");
        assertThat(r.tenantId()).isEqualTo(1L);
        assertThat(r.apiKeyId()).isEqualTo(123L);

        verify(authCache).read(123L);
    }

    @Test
    void create_shouldBindApiKeyToApplication() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        ApiKeyAuthCache authCache = mock(ApiKeyAuthCache.class);
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(60);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);

        ApiKeyService service = newService(store, passwordHasher, props, authCache, applicationScopePort);
        when(passwordHasher.encode(any())).thenReturn("encoded-secret");

        CreatedApiKeyResult created = service.create(1L, 2001L, "openapi-app");

        assertThat(created.id()).isPositive();
        ArgumentCaptor<AccountsApiKeyStore.ApiKey> captor = ArgumentCaptor.forClass(AccountsApiKeyStore.ApiKey.class);
        verify(store).insert(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().applicationId()).isEqualTo(2001L);
        verify(applicationScopePort).requireApplicationExists(1L, 2001L);
    }

    @Test
    void create_shouldNotPopulateActiveAuthCache() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        ApiKeyAuthCache authCache = mock(ApiKeyAuthCache.class);
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        CapturingPostCommitHook postCommitHook = new CapturingPostCommitHook();

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(60);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);

        ApiKeyService service = newService(store, passwordHasher, props, authCache, postCommitHook, applicationScopePort);
        when(passwordHasher.encode(any())).thenReturn("encoded-secret");

        CreatedApiKeyResult created = service.create(1L, 2001L, "openapi-app");

        assertThat(created.id()).isEqualTo(123L);
        verify(store).insert(any());
        verifyNoInteractions(authCache);
        postCommitHook.assertNothingCaptured();
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
                null,
                "test-key",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );

        when(store.findById(123L)).thenReturn(apiKey);
        when(passwordHasher.matches("secret", "hash")).thenReturn(true);

        ApiKeyAuthResult result = service.authenticate("lfk_123_secret");

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
        AccountsApiKeyStore.ApiKey apiKey = new AccountsApiKeyStore.ApiKey(
                123L,
                1L,
                null,
                "test-key",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );
        when(authCache.read(123L)).thenReturn(new ApiKeyAuthCache.Entry(1L, null, AccountsConstants.STATUS_ACTIVE, digest));
        when(store.findById(123L)).thenReturn(apiKey);
        when(passwordHasher.matches("secret", "hash")).thenReturn(true);
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
        AccountsApiKeyStore.ApiKey apiKey = new AccountsApiKeyStore.ApiKey(
                123L,
                1L,
                null,
                "test-key",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );
        when(authCache.read(123L)).thenReturn(new ApiKeyAuthCache.Entry(1L, null, AccountsConstants.STATUS_ACTIVE, digest));
        when(store.findById(123L)).thenReturn(apiKey);
        when(passwordHasher.matches("secret", "hash")).thenReturn(true);
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
                null,
                "test-key",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );
        when(store.findById(123L)).thenReturn(existing);

        ApiKeyInfoResult info = service.disable(1L, 123L);

        assertThat(info.status()).isEqualTo(AccountsConstants.STATUS_DISABLED);
        verify(store).update(argThat(apiKey -> AccountsConstants.STATUS_DISABLED.equals(apiKey.status())));
    }

    @Test
    void disable_shouldPopulateAuthCacheOnlyAfterCommit() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        ApiKeyAuthCache authCache = mock(ApiKeyAuthCache.class);
        CapturingPostCommitHook postCommitHook = new CapturingPostCommitHook();

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(60);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);

        ApiKeyService service = newService(
                store,
                mock(AccountsPasswordHasher.class),
                props,
                authCache,
                postCommitHook,
                mock(ApplicationScopePort.class)
        );

        AccountsApiKeyStore.ApiKey existing = new AccountsApiKeyStore.ApiKey(
                123L,
                1L,
                2001L,
                "test-key",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );
        when(store.findById(123L)).thenReturn(existing);

        service.disable(1L, 123L);

        verify(store).update(argThat(apiKey -> AccountsConstants.STATUS_DISABLED.equals(apiKey.status())));
        verifyNoInteractions(authCache);

        postCommitHook.runCaptured();

        verify(authCache).putDisabled(123L, 1L, 2001L, 60L);
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
                null,
                "test-key",
                "hash",
                AccountsConstants.STATUS_DISABLED,
                null,
                null
        );
        when(store.findById(123L)).thenReturn(existing);

        ApiKeyInfoResult info = service.enable(1L, 123L);

        assertThat(info.status()).isEqualTo(AccountsConstants.STATUS_ACTIVE);
        verify(store).update(argThat(apiKey -> AccountsConstants.STATUS_ACTIVE.equals(apiKey.status())));
    }

    @Test
    void enable_shouldEvictAuthCacheOnlyAfterCommit() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        ApiKeyAuthCache authCache = mock(ApiKeyAuthCache.class);
        CapturingPostCommitHook postCommitHook = new CapturingPostCommitHook();

        ApiKeyService service = newService(
                store,
                mock(AccountsPasswordHasher.class),
                new SecurityProperties(),
                authCache,
                postCommitHook,
                mock(ApplicationScopePort.class)
        );

        AccountsApiKeyStore.ApiKey existing = new AccountsApiKeyStore.ApiKey(
                123L,
                1L,
                2001L,
                "test-key",
                "hash",
                AccountsConstants.STATUS_DISABLED,
                null,
                null
        );
        when(store.findById(123L)).thenReturn(existing);

        service.enable(1L, 123L);

        verify(store).update(argThat(apiKey -> AccountsConstants.STATUS_ACTIVE.equals(apiKey.status())));
        verifyNoInteractions(authCache);

        postCommitHook.runCaptured();

        verify(authCache).evict(123L);
    }

    @Test
    void rotate_shouldEvictAuthCacheOnlyAfterCommit() {
        AccountsApiKeyStore store = mock(AccountsApiKeyStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        ApiKeyAuthCache authCache = mock(ApiKeyAuthCache.class);
        CapturingPostCommitHook postCommitHook = new CapturingPostCommitHook();

        SecurityProperties props = new SecurityProperties();
        props.getApiKey().setAuthCacheTtlSeconds(60);
        props.getApiKey().setLastUsedUpdateIntervalSeconds(0);

        ApiKeyService service = newService(
                store,
                passwordHasher,
                props,
                authCache,
                postCommitHook,
                mock(ApplicationScopePort.class)
        );

        AccountsApiKeyStore.ApiKey existing = new AccountsApiKeyStore.ApiKey(
                123L,
                1L,
                2001L,
                "test-key",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );
        when(store.findById(123L)).thenReturn(existing);
        when(passwordHasher.encode(any())).thenReturn("encoded-secret");

        service.rotate(1L, 123L);

        verify(store).update(any());
        verifyNoInteractions(authCache);

        postCommitHook.runCaptured();

        verify(authCache).evict(123L);
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
        return newService(store, passwordHasher, props, authCache, Clock.systemUTC(), mock(ApplicationScopePort.class));
    }

    private static ApiKeyService newService(
            AccountsApiKeyStore store,
            AccountsPasswordHasher passwordHasher,
            SecurityProperties props,
            ApiKeyAuthCache authCache,
            Clock clock
    ) {
        return newService(store, passwordHasher, props, authCache, clock, mock(ApplicationScopePort.class));
    }

    private static ApiKeyService newService(
            AccountsApiKeyStore store,
            AccountsPasswordHasher passwordHasher,
            SecurityProperties props,
            ApiKeyAuthCache authCache,
            ApplicationScopePort applicationScopePort
    ) {
        return newService(store, passwordHasher, props, authCache, Clock.systemUTC(), applicationScopePort);
    }

    private static ApiKeyService newService(
            AccountsApiKeyStore store,
            AccountsPasswordHasher passwordHasher,
            SecurityProperties props,
            ApiKeyAuthCache authCache,
            PostCommitHookPort postCommitHook,
            ApplicationScopePort applicationScopePort
    ) {
        return newService(store, passwordHasher, props, authCache, Clock.systemUTC(), postCommitHook, applicationScopePort);
    }

    private static ApiKeyService newService(
            AccountsApiKeyStore store,
            AccountsPasswordHasher passwordHasher,
            SecurityProperties props,
            ApiKeyAuthCache authCache,
            Clock clock,
            ApplicationScopePort applicationScopePort
    ) {
        return newService(store, passwordHasher, props, authCache, clock, action -> action.run(), applicationScopePort);
    }

    private static ApiKeyService newService(
            AccountsApiKeyStore store,
            AccountsPasswordHasher passwordHasher,
            SecurityProperties props,
            ApiKeyAuthCache authCache,
            Clock clock,
            PostCommitHookPort postCommitHook,
            ApplicationScopePort applicationScopePort
    ) {
        try {
            SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
            when(idGenerator.nextId()).thenReturn(123L);
            Constructor<ApiKeyService> constructor = ApiKeyService.class.getConstructor(
                    SnowflakeIdGenerator.class,
                    AccountsApiKeyStore.class,
                    AccountsPasswordHasher.class,
                    SecurityProperties.class,
                    ApiKeyAuthCache.class,
                    Clock.class,
                    PostCommitHookPort.class,
                    ApplicationScopePort.class
            );
            return constructor.newInstance(
                    idGenerator,
                    store,
                    passwordHasher,
                    props,
                    authCache,
                    clock,
                    postCommitHook,
                    applicationScopePort
            );
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class CapturingPostCommitHook implements PostCommitHookPort {

        private final AtomicReference<Runnable> captured = new AtomicReference<>();

        @Override
        public void run(Runnable action) {
            captured.set(action);
        }

        void runCaptured() {
            Runnable action = captured.get();
            if (action == null) {
                throw new AssertionError("expected post-commit action to be registered");
            }
            action.run();
        }

        void assertNothingCaptured() {
            assertThat(captured.get()).isNull();
        }
    }
}
