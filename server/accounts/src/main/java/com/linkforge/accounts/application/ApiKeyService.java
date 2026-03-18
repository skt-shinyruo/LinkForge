package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountsApiKeyStore;
import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.accounts.application.port.ApiKeyAuthCache;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.contract.api.AppErrorCode;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.openapi.OpenApiErrorCode;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.runtime.security.TenantGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final String API_KEY_PREFIX = "lfk";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest not available", e);
        }
    });

    private static final int MAX_API_KEY_LEN = 256;
    private static final int MAX_API_KEY_SECRET_LEN = 128;

    private final SnowflakeIdGenerator idGenerator;
    private final AccountsApiKeyStore apiKeyStore;
    private final AccountsPasswordHasher passwordHasher;
    private final TenantGuard tenantGuard;
    private final SecurityProperties securityProperties;
    private final ApiKeyAuthCache authCache;

    public ApiKeyService(
            SnowflakeIdGenerator idGenerator,
            AccountsApiKeyStore apiKeyStore,
            AccountsPasswordHasher passwordHasher,
            TenantGuard tenantGuard,
            SecurityProperties securityProperties,
            ApiKeyAuthCache authCache
    ) {
        this.idGenerator = idGenerator;
        this.apiKeyStore = apiKeyStore;
        this.passwordHasher = passwordHasher;
        this.tenantGuard = tenantGuard;
        this.securityProperties = securityProperties;
        this.authCache = authCache;
    }

    @Transactional
    public CreatedApiKey create(long tenantId, String name) {
        tenantGuard.requireCurrentTenant(tenantId);
        long id = idGenerator.nextId();
        String secret = randomSecret();
        String key = API_KEY_PREFIX + "_" + id + "_" + secret;

        AccountsApiKeyStore.ApiKey apiKey = new AccountsApiKeyStore.ApiKey(
                id,
                tenantId,
                name,
                passwordHasher.encode(secret),
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );
        apiKeyStore.insert(apiKey);

        String digest = sha256Base64Url(secret);
        long authCacheTtlSeconds = authCacheTtlSeconds();
        if (authCacheTtlSeconds > 0) {
            authCache.putActive(id, tenantId, digest, authCacheTtlSeconds);
        }

        return new CreatedApiKey(id, name, key);
    }

    public ApiKeyAuthResult authenticate(String apiKey) {
        Parsed parsed = parse(apiKey);
        String secretDigest = sha256Base64Url(parsed.secret);

        long authCacheTtlSeconds = authCacheTtlSeconds();
        ApiKeyAuthCache.Entry cached = authCacheTtlSeconds > 0
                ? authCache.read(parsed.id)
                : null;
        if (cached != null) {
            if (!AccountsConstants.STATUS_ACTIVE.equals(cached.status())) {
                throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_DISABLED);
            }
            if (!constantTimeEquals(secretDigest, cached.secretDigest())) {
                throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
            }
            tryUpdateLastUsedAtThrottled(parsed.id, null, false);
            return new ApiKeyAuthResult(cached.tenantId(), parsed.id);
        }

        AccountsApiKeyStore.ApiKey apiKeyRecord = apiKeyStore.findById(parsed.id);
        if (apiKeyRecord == null) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }

        if (!AccountsConstants.STATUS_ACTIVE.equals(apiKeyRecord.status())) {
            if (authCacheTtlSeconds > 0) {
                authCache.putDisabled(parsed.id, apiKeyRecord.tenantId() == null ? 0L : apiKeyRecord.tenantId(), authCacheTtlSeconds);
            }
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_DISABLED);
        }
        if (!passwordHasher.matches(parsed.secret, apiKeyRecord.keyHash())) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }

        if (authCacheTtlSeconds > 0) {
            authCache.putActive(parsed.id, apiKeyRecord.tenantId() == null ? 0L : apiKeyRecord.tenantId(), secretDigest, authCacheTtlSeconds);
        }
        tryUpdateLastUsedAtThrottled(parsed.id, apiKeyRecord.lastUsedAt(), true);

        return new ApiKeyAuthResult(apiKeyRecord.tenantId(), apiKeyRecord.id());
    }

    public List<ApiKeyInfo> list(long tenantId) {
        tenantGuard.requireCurrentTenant(tenantId);
        return apiKeyStore.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(e -> new ApiKeyInfo(e.id(), e.name(), e.status(), e.lastUsedAt(), e.createdAt()))
                .toList();
    }

    @Transactional
    public ApiKeyInfo disable(long tenantId, long apiKeyId) {
        tenantGuard.requireCurrentTenant(tenantId);
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
        if (authCacheTtlSeconds > 0) {
            authCache.putDisabled(apiKeyId, apiKey.tenantId() == null ? 0L : apiKey.tenantId(), authCacheTtlSeconds);
        }
        return new ApiKeyInfo(apiKey.id(), apiKey.name(), apiKey.status(), apiKey.lastUsedAt(), apiKey.createdAt());
    }

    @Transactional
    public ApiKeyInfo enable(long tenantId, long apiKeyId) {
        tenantGuard.requireCurrentTenant(tenantId);
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
        authCache.evict(apiKeyId);
        return new ApiKeyInfo(apiKey.id(), apiKey.name(), apiKey.status(), apiKey.lastUsedAt(), apiKey.createdAt());
    }

    @Transactional
    public CreatedApiKey rotate(long tenantId, long apiKeyId) {
        tenantGuard.requireCurrentTenant(tenantId);
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

        String digest = sha256Base64Url(secret);
        long authCacheTtlSeconds = authCacheTtlSeconds();
        if (authCacheTtlSeconds > 0) {
            authCache.putActive(apiKeyId, apiKey.tenantId() == null ? 0L : apiKey.tenantId(), digest, authCacheTtlSeconds);
        }

        return new CreatedApiKey(apiKey.id(), apiKey.name(), key);
    }

    public record CreatedApiKey(long id, String name, String apiKey) {
    }

    public record ApiKeyAuthResult(long tenantId, long apiKeyId) {
    }

    public record ApiKeyInfo(long id, String name, String status, LocalDateTime lastUsedAt, LocalDateTime createdAt) {
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

        LocalDateTime now = LocalDateTime.now();

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
                apiKey.name(),
                keyHash,
                status,
                apiKey.lastUsedAt(),
                apiKey.createdAt()
        );
    }

    private static String sha256Base64Url(String input) {
        if (input == null) {
            return "";
        }
        MessageDigest md = SHA_256.get();
        md.reset();
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return BASE64_URL.encodeToString(digest);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
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
}
