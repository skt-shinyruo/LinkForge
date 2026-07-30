package com.linkforge.accounts.infrastructure.cache;

import com.linkforge.accounts.application.port.AccountStatusCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 账户状态的 Redis 尽力缓存实现。
 *
 * <p>缓存不是账户状态的事实源。读取返回 {@code null} 时，既可能是未命中，也可能是空值、
 * 不兼容的序列化内容或 Redis 故障；应用层必须把它解释为“未知”并回源数据库，不能据此放行。
 * 命中的快照会在 TTL 内直接参与认证判断；写入或删除失败不会阻断业务事务，但可能让状态变更、
 * 注销或密码重置延迟到旧缓存过期后才完全生效。</p>
 *
 * <p>租户状态直接保存字符串；用户状态使用
 * {@code v1|tenantId|status|tokenVersion}。无法解析的用户状态会被尽力删除，以免持续污染后续读取。
 * 所有写入都要求正数 TTL，避免无期限保存可能过期的授权信息。</p>
 */
@Component
public class RedisAccountStatusCache implements AccountStatusCache {

    private static final Logger log = LoggerFactory.getLogger(RedisAccountStatusCache.class);

    private static final String TENANT_STATUS_KEY_PREFIX = "auth:tenant_status:";
    private static final String USER_STATUS_KEY_PREFIX = "auth:user_status:";
    private static final String USER_AUTH_STATE_VERSION = "v1";

    private final StringRedisTemplate redis;

    public RedisAccountStatusCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String readTenantStatus(long tenantId) {
        return read(tenantStatusKey(tenantId));
    }

    @Override
    public UserAuthState readUserAuthState(long userId) {
        String key = userStatusKey(userId);
        String raw = read(key);
        if (raw == null) {
            return null;
        }
        UserAuthState authState = parseUserAuthState(raw);
        if (authState != null) {
            return authState;
        }
        delete(key);
        return null;
    }

    @Override
    public void writeTenantStatus(long tenantId, String status, Duration ttl) {
        write(tenantStatusKey(tenantId), status, ttl);
    }

    @Override
    public void writeUserAuthState(long userId, long tenantId, String status, int tokenVersion, Duration ttl) {
        if (tenantId <= 0 || tokenVersion < 0) {
            return;
        }
        write(userStatusKey(userId), formatUserAuthState(new UserAuthState(tenantId, status, tokenVersion)), ttl);
    }

    @Override
    public void evictTenantStatus(long tenantId) {
        delete(tenantStatusKey(tenantId));
    }

    @Override
    public void evictUserStatus(long userId) {
        delete(userStatusKey(userId));
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

    private void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.debug("status cache delete failed: key={}, err={}", key, e.getMessage());
        }
    }

    private static UserAuthState parseUserAuthState(String raw) {
        String[] parts = raw.split("\\|", 4);
        if (parts.length != 4 || !USER_AUTH_STATE_VERSION.equals(parts[0])) {
            return null;
        }
        long tenantId;
        int tokenVersion;
        try {
            tenantId = Long.parseLong(parts[1]);
            tokenVersion = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            return null;
        }
        return new UserAuthState(tenantId, parts[2], tokenVersion);
    }

    private static String formatUserAuthState(UserAuthState authState) {
        return USER_AUTH_STATE_VERSION
                + "|" + authState.tenantId()
                + "|" + nullToEmpty(authState.status())
                + "|" + authState.tokenVersion();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String tenantStatusKey(long tenantId) {
        return TENANT_STATUS_KEY_PREFIX + tenantId;
    }

    private static String userStatusKey(long userId) {
        return USER_STATUS_KEY_PREFIX + userId;
    }
}
