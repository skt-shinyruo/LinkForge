package com.linkforge.accounts.application;

import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.accounts.infrastructure.persistence.entity.ApiKeyEntity;
import com.linkforge.accounts.infrastructure.persistence.mapper.ApiKeyMapper;
import com.linkforge.contract.api.AppErrorCode;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.openapi.OpenApiErrorCode;
import com.linkforge.foundation.security.TenantGuard;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
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

    private static final String AUTH_CACHE_KEY_PREFIX = "auth:api_key:";
    private static final String LAST_USED_TOKEN_KEY_PREFIX = "auth:api_key:last_used:";
    private static final int MAX_API_KEY_LEN = 256;
    private static final int MAX_API_KEY_SECRET_LEN = 128;

    private final SnowflakeIdGenerator idGenerator;
    private final ApiKeyMapper apiKeyMapper;
    private final PasswordEncoder passwordEncoder;
    private final TenantGuard tenantGuard;
    private final SecurityProperties securityProperties;
    private final StringRedisTemplate redis;

    public ApiKeyService(
            SnowflakeIdGenerator idGenerator,
            ApiKeyMapper apiKeyMapper,
            PasswordEncoder passwordEncoder,
            TenantGuard tenantGuard,
            SecurityProperties securityProperties,
            StringRedisTemplate redis
    ) {
        this.idGenerator = idGenerator;
        this.apiKeyMapper = apiKeyMapper;
        this.passwordEncoder = passwordEncoder;
        this.tenantGuard = tenantGuard;
        this.securityProperties = securityProperties;
        this.redis = redis;
    }

    @Transactional
    public CreatedApiKey create(long tenantId, String name) {
        tenantGuard.requireCurrentTenant(tenantId);
        long id = idGenerator.nextId();
        String secret = randomSecret();
        String key = API_KEY_PREFIX + "_" + id + "_" + secret;

        ApiKeyEntity e = new ApiKeyEntity();
        e.setId(id);
        e.setTenantId(tenantId);
        e.setName(name);
        e.setKeyHash(passwordEncoder.encode(secret));
        e.setStatus(AccountsConstants.STATUS_ACTIVE);
        apiKeyMapper.insert(e);

        // Pre-warm Redis auth cache to avoid first-call DB read + BCrypt in OpenAPI hot path.
        String digest = sha256Base64Url(secret);
        tryPutAuthCacheActive(id, tenantId, digest);

        return new CreatedApiKey(id, name, key);
    }

    public ApiKeyAuthResult authenticate(String apiKey) {
        Parsed parsed = parse(apiKey);
        String secretDigest = sha256Base64Url(parsed.secret);

        // 1) Redis auth cache: avoid per-request DB read + BCrypt in OpenAPI hot path
        AuthCacheEntry cached = readAuthCache(parsed.id);
        if (cached != null) {
            if (!AccountsConstants.STATUS_ACTIVE.equals(cached.status)) {
                throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_DISABLED);
            }
            if (!constantTimeEquals(secretDigest, cached.secretDigest)) {
                throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
            }

            // OpenAPI 高调用路径：last_used_at 采用节流写回，避免 DB 写热点
            tryUpdateLastUsedAtThrottled(parsed.id, null, false);

            return new ApiKeyAuthResult(cached.tenantId, parsed.id);
        }

        // 2) DB fallback: validate and backfill cache
        ApiKeyEntity e = apiKeyMapper.findById(parsed.id);
        if (e == null) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }

        if (!AccountsConstants.STATUS_ACTIVE.equals(e.getStatus())) {
            tryPutAuthCacheDisabled(parsed.id, e.getTenantId());
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_DISABLED);
        }
        if (!passwordEncoder.matches(parsed.secret, e.getKeyHash())) {
            throw new ApiKeyAuthException(OpenApiErrorCode.API_KEY_INVALID);
        }

        tryPutAuthCacheActive(parsed.id, e.getTenantId(), secretDigest);

        // OpenAPI 高调用路径：last_used_at 采用节流写回，避免 DB 写热点
        tryUpdateLastUsedAtThrottled(parsed.id, e.getLastUsedAt(), true);

        return new ApiKeyAuthResult(e.getTenantId(), e.getId());
    }

    public List<ApiKeyInfo> list(long tenantId) {
        tenantGuard.requireCurrentTenant(tenantId);
        return apiKeyMapper.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(e -> new ApiKeyInfo(e.getId(), e.getName(), e.getStatus(), e.getLastUsedAt(), e.getCreatedAt()))
                .toList();
    }

    @Transactional
    public ApiKeyInfo disable(long tenantId, long apiKeyId) {
        tenantGuard.requireCurrentTenant(tenantId);
        ApiKeyEntity e = apiKeyMapper.findById(apiKeyId);
        if (e == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        if (!tenantIdEquals(e.getTenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        if (!AccountsConstants.STATUS_DISABLED.equals(e.getStatus())) {
            e.setStatus(AccountsConstants.STATUS_DISABLED);
            apiKeyMapper.update(e);
        }
        tryPutAuthCacheDisabled(apiKeyId, e.getTenantId());
        return new ApiKeyInfo(e.getId(), e.getName(), e.getStatus(), e.getLastUsedAt(), e.getCreatedAt());
    }

    @Transactional
    public ApiKeyInfo enable(long tenantId, long apiKeyId) {
        tenantGuard.requireCurrentTenant(tenantId);
        ApiKeyEntity e = apiKeyMapper.findById(apiKeyId);
        if (e == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        if (!tenantIdEquals(e.getTenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        if (!AccountsConstants.STATUS_ACTIVE.equals(e.getStatus())) {
            e.setStatus(AccountsConstants.STATUS_ACTIVE);
            apiKeyMapper.update(e);
        }
        evictAuthCache(apiKeyId);
        return new ApiKeyInfo(e.getId(), e.getName(), e.getStatus(), e.getLastUsedAt(), e.getCreatedAt());
    }

    @Transactional
    public CreatedApiKey rotate(long tenantId, long apiKeyId) {
        tenantGuard.requireCurrentTenant(tenantId);
        ApiKeyEntity e = apiKeyMapper.findById(apiKeyId);
        if (e == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }
        if (!tenantIdEquals(e.getTenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "API Key 不存在");
        }

        String secret = randomSecret();
        String key = API_KEY_PREFIX + "_" + e.getId() + "_" + secret;
        e.setKeyHash(passwordEncoder.encode(secret));
        e.setStatus(AccountsConstants.STATUS_ACTIVE);
        apiKeyMapper.update(e);

        // Rotate should take effect across instances immediately: overwrite Redis cache with new digest.
        String digest = sha256Base64Url(secret);
        tryPutAuthCacheActive(apiKeyId, e.getTenantId(), digest);

        return new CreatedApiKey(e.getId(), e.getName(), key);
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

    private void tryUpdateLastUsedAtThrottled(ApiKeyEntity e) {
        if (e == null || e.getId() == null) {
            return;
        }
        tryUpdateLastUsedAtThrottled(e.getId(), e.getLastUsedAt(), true);
    }

    private void tryUpdateLastUsedAtThrottled(long apiKeyId, LocalDateTime lastUsedAtHint, boolean allowWriteWhenHintMissing) {
        long intervalSeconds = 300;
        try {
            if (securityProperties != null && securityProperties.getApiKey() != null) {
                intervalSeconds = securityProperties.getApiKey().getLastUsedUpdateIntervalSeconds();
            }
        } catch (Exception ignore) {
            // ignore
        }
        if (intervalSeconds < 0) {
            intervalSeconds = 0;
        }
        if (intervalSeconds == 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // Prefer Redis distributed throttle: cross-instance write reduction without DB read.
        if (redis != null) {
            String tokenKey = lastUsedTokenKey(apiKeyId);
            try {
                Boolean acquired = redis.opsForValue()
                        .setIfAbsent(tokenKey, "1", Duration.ofSeconds(intervalSeconds));
                if (Boolean.TRUE.equals(acquired)) {
                    try {
                        apiKeyMapper.updateLastUsedAt(apiKeyId, now);
                    } catch (Exception ex) {
                        // best-effort：避免影响主链路鉴权；失败时尝试释放 token 以便更快重试
                        try {
                            redis.delete(tokenKey);
                        } catch (Exception ignore) {
                            // ignore
                        }
                        log.debug("update api_key last_used_at failed: id={}, err={}", apiKeyId, ex.getMessage());
                    }
                }
                return;
            } catch (Exception ex) {
                // Redis 异常：降级到 DB-hint 节流（若可用）
                log.debug("last_used_at throttle token read/write failed: id={}, err={}", apiKeyId, ex.getMessage());
            }
        }

        if (lastUsedAtHint == null && !allowWriteWhenHintMissing) {
            // Cache-hit + Redis unavailable: avoid write amplification / DB hot spot.
            return;
        }

        // Fallback: use DB last_used_at hint (only available when we already read the entity)
        if (lastUsedAtHint != null && !lastUsedAtHint.plusSeconds(intervalSeconds).isBefore(now)) {
            return;
        }
        try {
            apiKeyMapper.updateLastUsedAt(apiKeyId, now);
        } catch (Exception ex) {
            // best-effort：避免影响主链路鉴权
            log.debug("update api_key last_used_at failed: id={}, err={}", apiKeyId, ex.getMessage());
        }
    }

    private static boolean tenantIdEquals(Long actual, long expected) {
        return actual != null && actual == expected;
    }

    private record AuthCacheEntry(long tenantId, String status, String secretDigest) {
        private static AuthCacheEntry tryParse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String[] parts = raw.split("\\|", 4);
            if (parts.length != 4) {
                return null;
            }
            if (!"v1".equals(parts[0])) {
                return null;
            }
            long tenantId;
            try {
                tenantId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
            String status = parts[2];
            String digest = parts[3];
            return new AuthCacheEntry(tenantId, status, digest);
        }

        private String format() {
            return "v1|" + tenantId + "|" + (status == null ? "" : status) + "|" + (secretDigest == null ? "" : secretDigest);
        }
    }

    private AuthCacheEntry readAuthCache(long apiKeyId) {
        if (redis == null) {
            return null;
        }
        long ttlSeconds = authCacheTtlSeconds();
        if (ttlSeconds <= 0) {
            return null;
        }
        String key = authCacheKey(apiKeyId);
        String raw;
        try {
            raw = redis.opsForValue().get(key);
        } catch (Exception e) {
            // 缓存异常：降级为未命中，让主链路回源
            log.debug("api key auth cache read failed: id={}, err={}", apiKeyId, e.getMessage());
            return null;
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        AuthCacheEntry parsed = AuthCacheEntry.tryParse(raw);
        if (parsed != null) {
            return parsed;
        }
        // Cache corruption: best-effort clean up.
        try {
            redis.delete(key);
        } catch (Exception ignore) {
            // ignore
        }
        return null;
    }

    private void tryPutAuthCacheActive(long apiKeyId, Long tenantId, String secretDigest) {
        if (redis == null) {
            return;
        }
        if (tenantId == null) {
            return;
        }
        long ttlSeconds = authCacheTtlSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        if (secretDigest == null || secretDigest.isBlank()) {
            return;
        }
        AuthCacheEntry entry = new AuthCacheEntry(tenantId, AccountsConstants.STATUS_ACTIVE, secretDigest);
        try {
            redis.opsForValue().set(authCacheKey(apiKeyId), entry.format(), Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.debug("api key auth cache write failed: id={}, err={}", apiKeyId, e.getMessage());
        }
    }

    private void tryPutAuthCacheDisabled(long apiKeyId, Long tenantId) {
        if (redis == null) {
            return;
        }
        if (tenantId == null) {
            return;
        }
        long ttlSeconds = authCacheTtlSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        AuthCacheEntry entry = new AuthCacheEntry(tenantId, AccountsConstants.STATUS_DISABLED, "");
        try {
            redis.opsForValue().set(authCacheKey(apiKeyId), entry.format(), Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.debug("api key auth cache write(disabled) failed: id={}, err={}", apiKeyId, e.getMessage());
        }
    }

    private void evictAuthCache(long apiKeyId) {
        if (redis == null) {
            return;
        }
        try {
            redis.delete(authCacheKey(apiKeyId));
        } catch (Exception e) {
            log.debug("api key auth cache evict failed: id={}, err={}", apiKeyId, e.getMessage());
        }
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
            // ignore
        }
        if (ttlSeconds < 0) {
            ttlSeconds = 0;
        }
        return ttlSeconds;
    }

    private static String authCacheKey(long apiKeyId) {
        return AUTH_CACHE_KEY_PREFIX + apiKeyId;
    }

    private static String lastUsedTokenKey(long apiKeyId) {
        return LAST_USED_TOKEN_KEY_PREFIX + apiKeyId;
    }
}
