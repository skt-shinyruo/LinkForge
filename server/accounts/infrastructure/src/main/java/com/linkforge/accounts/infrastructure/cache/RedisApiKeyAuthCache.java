package com.linkforge.accounts.infrastructure.cache;

import com.linkforge.accounts.application.port.ApiKeyAuthCache;
import com.linkforge.accounts.domain.AccountsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * API Key 认证所需的 Redis 辅助状态。
 *
 * <p>{@code auth:api_key:*} 是禁用态负缓存，不是成功认证缓存：当前实现只写入禁用记录，
 * 且应用层即使读到历史 ACTIVE 条目也仍会回源数据库并校验凭据摘要。因而缓存不可用、未命中、
 * 空值或坏值统一返回 {@code null}，只会增加数据库读取，不会绕过密钥和状态校验。</p>
 *
 * <p>读取兼容旧 {@code v1|tenantId|status|secretDigest} 格式；该格式没有
 * {@code applicationId}，解析结果保留为 {@code null}。新格式为
 * {@code v2|tenantId|applicationId|status|secretDigest}，其中空 applicationId 不会被推断或补绑。
 * 这种兼容仅用于安全地识别禁用状态，历史未绑定 Key 仍由权威记录判定为无效。</p>
 *
 * <p>{@code auth:api_key:last_used:*} 使用带 TTL 的 SETNX 作为写回节流令牌。
 * 其三态结果明确区分“取得令牌”“已有令牌”和“Redis 不可用”，让应用层在故障时使用数据库时间提示降级。
 * 所有 Redis 写入、删除均为尽力操作，不参与业务事务。</p>
 */
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
    public void putDisabled(long apiKeyId, long tenantId, Long applicationId, long ttlSeconds) {
        if (tenantId <= 0 || ttlSeconds <= 0) {
            return;
        }
        write(apiKeyId, new Entry(tenantId, applicationId, AccountsConstants.STATUS_DISABLED, ""), ttlSeconds);
    }

    @Override
    public void evict(long apiKeyId) {
        try {
            redis.delete(authCacheKey(apiKeyId));
        } catch (Exception e) {
            log.warn("api key auth cache evict failed: id={}, err={}", apiKeyId, e.getMessage());
        }
    }

    /**
     * 尝试取得 {@code last_used_at} 写回令牌。
     *
     * @return 非正间隔或令牌已存在时为 {@link LastUsedTokenResult#NOT_ACQUIRED}；
     *         Redis 读写失败时为 {@link LastUsedTokenResult#CACHE_UNAVAILABLE}
     */
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
            log.warn("api key auth cache write failed: id={}, err={}", apiKeyId, e.getMessage());
        }
    }

    private static Entry parse(String raw) {
        String[] parts = raw.split("\\|", 5);
        if (parts.length == 4 && "v1".equals(parts[0])) {
            long tenantId;
            try {
                tenantId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
            return new Entry(tenantId, null, parts[2], parts[3]);
        }
        if (parts.length != 5 || !"v2".equals(parts[0])) {
            return null;
        }
        long tenantId;
        try {
            tenantId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        Long applicationId = null;
        if (!parts[2].isBlank()) {
            try {
                applicationId = Long.parseLong(parts[2]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return new Entry(tenantId, applicationId, parts[3], parts[4]);
    }

    private static String format(Entry entry) {
        return "v2|"
                + entry.tenantId()
                + "|"
                + (entry.applicationId() == null ? "" : entry.applicationId())
                + "|"
                + nullToEmpty(entry.status())
                + "|"
                + nullToEmpty(entry.secretDigest());
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
