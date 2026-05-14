package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountsApiKeyStore;
import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.accounts.application.port.ApiKeyAuthCache;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.contract.api.AppErrorCode;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.openapi.OpenApiErrorCode;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.security.ApiKeyAuthenticationException;
import com.linkforge.foundation.security.ApiKeyAuthenticationFailure;
import com.linkforge.foundation.security.ApiKeyAuthenticationResult;
import com.linkforge.foundation.security.ApiKeyAuthenticator;
import com.linkforge.foundation.tx.PostCommitHookPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

@Service
public class ApiKeyService implements ApiKeyAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final String API_KEY_PREFIX = "lfk";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int MAX_API_KEY_LEN = 256;
    private static final int MAX_API_KEY_SECRET_LEN = 128;

    private final SnowflakeIdGenerator idGenerator;
    private final AccountsApiKeyStore apiKeyStore;
    private final AccountsPasswordHasher passwordHasher;
    private final SecurityProperties securityProperties;
    private final ApiKeyAuthCache authCache;
    private final Clock clock;
    private final PostCommitHookPort postCommitHookPort;
    private final ApplicationScopePort applicationScopePort;

    public ApiKeyService(
            SnowflakeIdGenerator idGenerator,
            AccountsApiKeyStore apiKeyStore,
            AccountsPasswordHasher passwordHasher,
            SecurityProperties securityProperties,
            ApiKeyAuthCache authCache,
            Clock clock,
            PostCommitHookPort postCommitHookPort,
            ApplicationScopePort applicationScopePort
    ) {
        this.idGenerator = idGenerator;
        this.apiKeyStore = apiKeyStore;
        this.passwordHasher = passwordHasher;
        this.securityProperties = securityProperties;
        this.authCache = authCache;
        this.clock = clock;
        this.postCommitHookPort = postCommitHookPort;
        this.applicationScopePort = applicationScopePort;
    }

    @Transactional
    public CreatedApiKeyResult create(long tenantId, String name) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "applicationId 不能为空");
    }

    @Transactional
    public CreatedApiKeyResult create(long tenantId, long applicationId, String name) {
        applicationScopePort.requireApplicationExists(tenantId, applicationId);
        long id = idGenerator.nextId();
        String secret = randomSecret();
        String key = API_KEY_PREFIX + "_" + id + "_" + secret;

        AccountsApiKeyStore.ApiKey apiKey = new AccountsApiKeyStore.ApiKey(
                id,
                tenantId,
                applicationId,
                name,
                passwordHasher.encode(secret),
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );
        apiKeyStore.insert(apiKey);

        return new CreatedApiKeyResult(id, name, key);
    }

    public ApiKeyAuthResult authenticate(String apiKey) {
        Parsed parsed = parse(apiKey);

        long authCacheTtlSeconds = authCacheTtlSeconds();
        ApiKeyAuthCache.Entry cached = authCacheTtlSeconds > 0
                ? authCache.read(parsed.id)
                : null;
        if (cached != null && !AccountsConstants.STATUS_ACTIVE.equals(cached.status())) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_DISABLED);
        }

        AccountsApiKeyStore.ApiKey apiKeyRecord = apiKeyStore.findById(parsed.id);
        if (apiKeyRecord == null) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }

        if (!AccountsConstants.STATUS_ACTIVE.equals(apiKeyRecord.status())) {
            if (authCacheTtlSeconds > 0) {
                authCache.putDisabled(
                        parsed.id,
                        apiKeyRecord.tenantId() == null ? 0L : apiKeyRecord.tenantId(),
                        apiKeyRecord.applicationId(),
                        authCacheTtlSeconds
                );
            }
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_DISABLED);
        }
        if (!passwordHasher.matches(parsed.secret, apiKeyRecord.keyHash())) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }
        if (apiKeyRecord.applicationId() == null) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }

        tryUpdateLastUsedAtThrottled(parsed.id, apiKeyRecord.lastUsedAt(), true);

        return new ApiKeyAuthResult(apiKeyRecord.tenantId(), apiKeyRecord.applicationId(), apiKeyRecord.id());
    }

    @Override
    public ApiKeyAuthenticationResult authenticateApiKey(String apiKey) {
        try {
            ApiKeyAuthResult result = authenticate(apiKey);
            return new ApiKeyAuthenticationResult(result.tenantId(), result.applicationId(), result.apiKeyId());
        } catch (ApiKeyAuthException e) {
            throw new ApiKeyAuthenticationException(toAuthenticationFailure(e.errorCode()));
        }
    }

    private static ApiKeyAuthenticationFailure toAuthenticationFailure(AppErrorCode errorCode) {
        if (OpenApiErrorCode.API_KEY_DISABLED.equals(errorCode)) {
            return ApiKeyAuthenticationFailure.DISABLED;
        }
        return ApiKeyAuthenticationFailure.INVALID;
    }

    public List<ApiKeyInfoResult> list(long tenantId) {
        return list(tenantId, null);
    }

    public List<ApiKeyInfoResult> list(long tenantId, Long applicationId) {
        return apiKeyStore.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(e -> applicationId == null || applicationId.equals(e.applicationId()))
                .map(e -> new ApiKeyInfoResult(e.id(), e.applicationId(), e.name(), e.status(), e.lastUsedAt(), e.createdAt()))
                .toList();
    }

    @Transactional
    public ApiKeyInfoResult disable(long tenantId, long apiKeyId) {
        AccountsApiKeyStore.ApiKey apiKey = apiKeyStore.findById(apiKeyId);
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        if (!tenantIdEquals(apiKey.tenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        if (!AccountsConstants.STATUS_DISABLED.equals(apiKey.status())) {
            apiKey = withStatus(apiKey, AccountsConstants.STATUS_DISABLED);
            apiKeyStore.update(apiKey);
        }
        long authCacheTtlSeconds = authCacheTtlSeconds();
        putDisabledAfterCommit(
                apiKeyId,
                apiKey.tenantId() == null ? 0L : apiKey.tenantId(),
                apiKey.applicationId(),
                authCacheTtlSeconds
        );
        return new ApiKeyInfoResult(apiKey.id(), apiKey.applicationId(), apiKey.name(), apiKey.status(), apiKey.lastUsedAt(), apiKey.createdAt());
    }

    @Transactional
    public ApiKeyInfoResult enable(long tenantId, long apiKeyId) {
        AccountsApiKeyStore.ApiKey apiKey = apiKeyStore.findById(apiKeyId);
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        if (!tenantIdEquals(apiKey.tenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        if (!AccountsConstants.STATUS_ACTIVE.equals(apiKey.status())) {
            apiKey = withStatus(apiKey, AccountsConstants.STATUS_ACTIVE);
            apiKeyStore.update(apiKey);
        }
        evictAfterCommit(apiKeyId);
        return new ApiKeyInfoResult(apiKey.id(), apiKey.applicationId(), apiKey.name(), apiKey.status(), apiKey.lastUsedAt(), apiKey.createdAt());
    }

    @Transactional
    public CreatedApiKeyResult rotate(long tenantId, long apiKeyId) {
        AccountsApiKeyStore.ApiKey apiKey = apiKeyStore.findById(apiKeyId);
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        if (!tenantIdEquals(apiKey.tenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }

        String secret = randomSecret();
        String key = API_KEY_PREFIX + "_" + apiKey.id() + "_" + secret;
        apiKeyStore.update(withKeyHashAndStatus(apiKey, passwordHasher.encode(secret), AccountsConstants.STATUS_ACTIVE));

        evictAfterCommit(apiKeyId);

        return new CreatedApiKeyResult(apiKey.id(), apiKey.name(), key);
    }

    public static class ApiKeyAuthException extends RuntimeException {
        private final AppErrorCode errorCode;

        public ApiKeyAuthException(AppErrorCode errorCode) {
            super(errorCode.getDefaultMessage());
            this.errorCode = errorCode;
        }

        public AppErrorCode errorCode() {
            return errorCode;
        }
    }

    private static class Parsed {
        private final long id;
        private final String secret;

        private Parsed(long id, String secret) {
            this.id = id;
            this.secret = secret;
        }
    }

    private static Parsed parse(String apiKey) {
        if (apiKey == null) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }
        if (apiKey.length() > MAX_API_KEY_LEN) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }
        String[] parts = apiKey.split("_", 3);
        if (parts.length != 3) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }
        if (!API_KEY_PREFIX.equals(parts[0])) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }
        long id;
        try {
            id = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }
        String secret = parts[2];
        if (secret.isBlank()) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }
        if (secret.length() > MAX_API_KEY_SECRET_LEN) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }
        return new Parsed(id, secret);
    }

    private static String randomSecret() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private void tryUpdateLastUsedAtThrottled(long apiKeyId, LocalDateTime lastUsedAtHint, boolean allowWriteWhenHintMissing) {
        long intervalSeconds = 300;
        try {
            if (securityProperties != null && securityProperties.getApiKey() != null) {
                intervalSeconds = securityProperties.getApiKey().getLastUsedUpdateIntervalSeconds();
            }
        } catch (Exception ignore) {
        }
        if (intervalSeconds < 0) {
            intervalSeconds = 0;
        }
        if (intervalSeconds == 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        ApiKeyAuthCache.LastUsedTokenResult tokenResult = authCache.tryAcquireLastUsedToken(apiKeyId, intervalSeconds);
        if (tokenResult == ApiKeyAuthCache.LastUsedTokenResult.ACQUIRED) {
            try {
                apiKeyStore.updateLastUsedAt(apiKeyId, now);
            } catch (Exception ex) {
                authCache.releaseLastUsedToken(apiKeyId);
                log.debug("update api_key last_used_at failed: id={}, err={}", apiKeyId, ex.getMessage());
            }
            return;
        }
        if (tokenResult == ApiKeyAuthCache.LastUsedTokenResult.NOT_ACQUIRED) {
            return;
        }

        if (lastUsedAtHint == null && !allowWriteWhenHintMissing) {
            return;
        }

        if (lastUsedAtHint != null && !lastUsedAtHint.plusSeconds(intervalSeconds).isBefore(now)) {
            return;
        }
        try {
            apiKeyStore.updateLastUsedAt(apiKeyId, now);
        } catch (Exception ex) {
            log.debug("update api_key last_used_at failed: id={}, err={}", apiKeyId, ex.getMessage());
        }
    }

    private static boolean tenantIdEquals(Long actual, long expected) {
        return actual != null && actual == expected;
    }

    private static AccountsApiKeyStore.ApiKey withStatus(AccountsApiKeyStore.ApiKey apiKey, String status) {
        return new AccountsApiKeyStore.ApiKey(
                apiKey.id(),
                apiKey.tenantId(),
                apiKey.applicationId(),
                apiKey.name(),
                apiKey.keyHash(),
                status,
                apiKey.lastUsedAt(),
                apiKey.createdAt()
        );
    }

    private static AccountsApiKeyStore.ApiKey withKeyHashAndStatus(
            AccountsApiKeyStore.ApiKey apiKey,
            String keyHash,
            String status
    ) {
        return new AccountsApiKeyStore.ApiKey(
                apiKey.id(),
                apiKey.tenantId(),
                apiKey.applicationId(),
                apiKey.name(),
                keyHash,
                status,
                apiKey.lastUsedAt(),
                apiKey.createdAt()
        );
    }

    private long authCacheTtlSeconds() {
        long ttlSeconds = 60;
        try {
            if (securityProperties != null && securityProperties.getApiKey() != null) {
                ttlSeconds = securityProperties.getApiKey().getAuthCacheTtlSeconds();
            }
        } catch (Exception ignore) {
        }
        if (ttlSeconds < 0) {
            ttlSeconds = 0;
        }
        return ttlSeconds;
    }

    private void putDisabledAfterCommit(long apiKeyId, long tenantId, Long applicationId, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        runAfterCommit(() -> authCache.putDisabled(apiKeyId, tenantId, applicationId, ttlSeconds));
    }

    private void evictAfterCommit(long apiKeyId) {
        runAfterCommit(() -> authCache.evict(apiKeyId));
    }

    private void runAfterCommit(Runnable action) {
        if (postCommitHookPort == null) {
            action.run();
            return;
        }
        postCommitHookPort.run(action);
    }
}
