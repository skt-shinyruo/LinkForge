package com.linkforge.redirect.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkCachePort.LookupResult;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.foundation.config.RedirectProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LinkCacheService implements LinkCachePort {

    private static final Logger log = LoggerFactory.getLogger(LinkCacheService.class);
    private static final String PREFIX = "link:code:";
    private static final String HOST_PREFIX = "link:host:";
    private static final String NOT_FOUND_SENTINEL = "__lf_not_found__";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RedirectProperties redirectProperties;

    public LinkCacheService(StringRedisTemplate redis, ObjectMapper objectMapper, RedirectProperties redirectProperties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.redirectProperties = redirectProperties;
    }

    /**
     * 从 Redis 读取缓存（包含“短码不存在”的负缓存）。
     *
     * <p>说明：负缓存用于抵御随机短码扫描导致的缓存穿透。</p>
     */
    @Override
    public LookupResult lookup(String code) {
        return lookup(null, code);
    }

    @Override
    public LookupResult lookup(String host, String code) {
        if (code == null || code.isBlank()) {
            return LookupResult.miss();
        }

        String raw;
        try {
            raw = redis.opsForValue().get(key(host, code));
        } catch (Exception e) {
            // 缓存异常：降级为未命中，让主链路回源
            log.debug("cache read failed: host={}, code={}, err={}", host, code, e.getMessage());
            return LookupResult.miss();
        }
        if (raw == null) {
            return LookupResult.miss();
        }
        if (NOT_FOUND_SENTINEL.equals(raw)) {
            return LookupResult.negativeHit();
        }
        try {
            return LookupResult.hit(objectMapper.readValue(raw, LinkMeta.class));
        } catch (Exception e) {
            // 缓存反序列化失败时，直接当作未命中并清理
            try {
                redis.delete(key(host, code));
            } catch (Exception ex) {
                log.debug("cache delete failed after deserialize error: host={}, code={}, err={}", host, code, ex.getMessage());
            }
            log.debug("cache deserialize failed: host={}, code={}, err={}", host, code, e.getMessage());
            return LookupResult.miss();
        }
    }

    public LinkMeta get(String code) {
        return lookup(code).meta();
    }

    public void put(LinkMeta meta) {
        tryPut(meta);
    }

    /**
     * 尝试写入缓存；成功返回 true，失败返回 false（不抛异常）。
     *
     * <p>说明：提供给 projector/job 等“需要感知写入是否成功以便重试”的场景。</p>
     */
    @Override
    public boolean tryPut(LinkMeta meta) {
        return tryPut(null, meta);
    }

    @Override
    public boolean tryPut(String host, LinkMeta meta) {
        if (meta == null || meta.code() == null || meta.code().isBlank()) {
            return true;
        }
        try {
            String raw = objectMapper.writeValueAsString(meta);
            redis.opsForValue().set(
                    key(resolveCacheHost(host, meta), meta.code()),
                    raw,
                    Duration.ofSeconds(redirectProperties.getCacheTtlSeconds())
            );
            return true;
        } catch (Exception e) {
            // 缓存写入失败不应影响主链路
            log.debug(
                    "cache write failed: host={}, code={}, tenantId={}, linkId={}, err={}",
                    resolveCacheHost(host, meta),
                    meta == null ? null : meta.code(),
                    meta == null ? null : meta.tenantId(),
                    meta == null ? null : meta.id(),
                    e.getMessage()
            );
            return false;
        }
    }

    @Override
    public void markNotFound(String code) {
        markNotFound(null, code);
    }

    @Override
    public void markNotFound(String host, String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        long ttlSeconds = redirectProperties == null ? 0 : redirectProperties.getNotFoundCacheTtlSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        try {
            redis.opsForValue().set(key(host, code), NOT_FOUND_SENTINEL, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            // 负缓存写入失败不应影响主链路
            log.debug("cache write not-found failed: host={}, code={}, err={}", host, code, e.getMessage());
        }
    }

    public void evict(String code) {
        tryEvict(code);
    }

    /**
     * 尝试驱逐缓存；成功返回 true，失败返回 false（不抛异常）。
     */
    @Override
    public boolean tryEvict(String code) {
        return tryEvict(null, code);
    }

    @Override
    public boolean tryEvict(String host, String code) {
        if (code == null || code.isBlank()) {
            return true;
        }
        try {
            redis.delete(key(host, code));
            return true;
        } catch (Exception e) {
            log.debug("cache evict failed: host={}, code={}, err={}", host, code, e.getMessage());
            return false;
        }
    }

    private static String key(String code) {
        return PREFIX + code;
    }

    private static String key(String host, String code) {
        String normalizedHost = normalizeHost(host);
        if (normalizedHost == null) {
            return key(code);
        }
        return HOST_PREFIX + normalizedHost + ":code:" + code;
    }

    private static String resolveCacheHost(String requestedHost, LinkMeta meta) {
        String host = normalizeHost(requestedHost);
        if (host != null) {
            return host;
        }
        return meta == null ? null : normalizeHost(meta.hostname());
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        String normalized = host.trim().toLowerCase();
        if (normalized.isBlank()) {
            return null;
        }
        int colonIndex = normalized.indexOf(':');
        if (colonIndex > 0) {
            normalized = normalized.substring(0, colonIndex);
        }
        return normalized.isBlank() ? null : normalized;
    }
}
