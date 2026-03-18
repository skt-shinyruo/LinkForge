package com.linkforge.accounts.infrastructure.cache;

import com.linkforge.accounts.application.port.AccountStatusCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisAccountStatusCache implements AccountStatusCache {

    private static final Logger log = LoggerFactory.getLogger(RedisAccountStatusCache.class);

    private static final String TENANT_STATUS_KEY_PREFIX = "auth:tenant_status:";
    private static final String USER_STATUS_KEY_PREFIX = "auth:user_status:";

    private final StringRedisTemplate redis;

    public RedisAccountStatusCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String readTenantStatus(long tenantId) {
        return read(tenantStatusKey(tenantId));
    }

    @Override
    public String readUserStatus(long userId) {
        return read(userStatusKey(userId));
    }

    @Override
    public void writeTenantStatus(long tenantId, String status, Duration ttl) {
        write(tenantStatusKey(tenantId), status, ttl);
    }

    @Override
    public void writeUserStatus(long userId, String status, Duration ttl) {
        write(userStatusKey(userId), status, ttl);
    }

    private String read(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            String value = redis.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        } catch (Exception e) {
            log.debug("status cache read failed: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    private void write(String key, String status, Duration ttl) {
        if (key == null || key.isBlank() || status == null || status.isBlank()) {
            return;
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            redis.opsForValue().set(key, status, ttl);
        } catch (Exception e) {
            log.debug("status cache write failed: key={}, err={}", key, e.getMessage());
        }
    }

    private static String tenantStatusKey(long tenantId) {
        return TENANT_STATUS_KEY_PREFIX + tenantId;
    }

    private static String userStatusKey(long userId) {
        return USER_STATUS_KEY_PREFIX + userId;
    }
}
