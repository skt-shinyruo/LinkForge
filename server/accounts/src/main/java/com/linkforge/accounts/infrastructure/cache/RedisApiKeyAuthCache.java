package com.linkforge.accounts.infrastructure.cache;

import com.linkforge.accounts.application.port.ApiKeyAuthCache;
import com.linkforge.accounts.domain.AccountsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisApiKeyAuthCache implements ApiKeyAuthCache {

    private static final Logger log = LoggerFactory.getLogger(RedisApiKeyAuthCache.class);

    private static final String AUTH_CACHE_KEY_PREFIX = "auth:api_key:";
    private static final String LAST_USED_TOKEN_KEY_PREFIX = "auth:api_key:last_used:";

    private final StringRedisTemplate redis;

    public RedisApiKeyAuthCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Entry read(long apiKeyId) {
        String key = authCacheKey(apiKeyId);
        try {
            String raw = redis.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            Entry entry = parse(raw);
            if (entry != null) {
                return entry;
            }
            redis.delete(key);
            return null;
        } catch (Exception e) {
            log.debug("api key auth cache read failed: id={}, err={}", apiKeyId, e.getMessage());
            return null;
        }
    }

    @Override
    public void putActive(long apiKeyId, long tenantId, String secretDigest, long ttlSeconds) {
        if (tenantId <= 0 || secretDigest == null || secretDigest.isBlank() || ttlSeconds <= 0) {
            return;
        }
        write(apiKeyId, new Entry(tenantId, AccountsConstants.STATUS_ACTIVE, secretDigest), ttlSeconds);
    }

    @Override
    public void putDisabled(long apiKeyId, long tenantId, long ttlSeconds) {
        if (tenantId <= 0 || ttlSeconds <= 0) {
            return;
        }
        write(apiKeyId, new Entry(tenantId, AccountsConstants.STATUS_DISABLED, ""), ttlSeconds);
    }

    @Override
    public void evict(long apiKeyId) {
        try {
            redis.delete(authCacheKey(apiKeyId));
        } catch (Exception e) {
            log.debug("api key auth cache evict failed: id={}, err={}", apiKeyId, e.getMessage());
        }
    }

    @Override
    public LastUsedTokenResult tryAcquireLastUsedToken(long apiKeyId, long intervalSeconds) {
        if (intervalSeconds <= 0) {
            return LastUsedTokenResult.NOT_ACQUIRED;
        }
        try {
            Boolean acquired = redis.opsForValue()
                    .setIfAbsent(lastUsedTokenKey(apiKeyId), "1", Duration.ofSeconds(intervalSeconds));
            return Boolean.TRUE.equals(acquired)
                    ? LastUsedTokenResult.ACQUIRED
                    : LastUsedTokenResult.NOT_ACQUIRED;
        } catch (Exception e) {
            log.debug("last_used_at throttle token read/write failed: id={}, err={}", apiKeyId, e.getMessage());
            return LastUsedTokenResult.CACHE_UNAVAILABLE;
        }
    }

    @Override
    public void releaseLastUsedToken(long apiKeyId) {
        try {
            redis.delete(lastUsedTokenKey(apiKeyId));
        } catch (Exception e) {
            log.debug("last_used_at throttle token release failed: id={}, err={}", apiKeyId, e.getMessage());
        }
    }

    private void write(long apiKeyId, Entry entry, long ttlSeconds) {
        try {
            redis.opsForValue().set(authCacheKey(apiKeyId), format(entry), Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.debug("api key auth cache write failed: id={}, err={}", apiKeyId, e.getMessage());
        }
    }

    private static Entry parse(String raw) {
        String[] parts = raw.split("\\|", 4);
        if (parts.length != 4 || !"v1".equals(parts[0])) {
            return null;
        }
        long tenantId;
        try {
            tenantId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        return new Entry(tenantId, parts[2], parts[3]);
    }

    private static String format(Entry entry) {
        return "v1|" + entry.tenantId() + "|" + nullToEmpty(entry.status()) + "|" + nullToEmpty(entry.secretDigest());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String authCacheKey(long apiKeyId) {
        return AUTH_CACHE_KEY_PREFIX + apiKeyId;
    }

    private static String lastUsedTokenKey(long apiKeyId) {
        return LAST_USED_TOKEN_KEY_PREFIX + apiKeyId;
    }
}
