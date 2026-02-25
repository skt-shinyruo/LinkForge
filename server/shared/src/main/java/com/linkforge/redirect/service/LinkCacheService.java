package com.linkforge.redirect.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.platform.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LinkCacheService {

    private static final Logger log = LoggerFactory.getLogger(LinkCacheService.class);
    private static final String PREFIX = "link:code:";
    private static final String NOT_FOUND_SENTINEL = "__lf_not_found__";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public LinkCacheService(StringRedisTemplate redis, ObjectMapper objectMapper, AppProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public record LookupResult(LinkMeta meta, boolean notFound) {
        public static LookupResult hit(LinkMeta meta) {
            return new LookupResult(meta, false);
        }

        public static LookupResult negativeHit() {
            return new LookupResult(null, true);
        }

        public static LookupResult miss() {
            return new LookupResult(null, false);
        }

        public boolean hit() {
            return meta != null;
        }
    }

    /**
     * 从 Redis 读取缓存（包含“短码不存在”的负缓存）。
     *
     * <p>说明：负缓存用于抵御随机短码扫描导致的缓存穿透。</p>
     */
    public LookupResult lookup(String code) {
        if (code == null || code.isBlank()) {
            return LookupResult.miss();
        }

        String raw;
        try {
            raw = redis.opsForValue().get(key(code));
        } catch (Exception e) {
            // 缓存异常：降级为未命中，让主链路回源
            log.debug("cache read failed: code={}, err={}", code, e.getMessage());
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
                redis.delete(key(code));
            } catch (Exception ex) {
                log.debug("cache delete failed after deserialize error: code={}, err={}", code, ex.getMessage());
            }
            log.debug("cache deserialize failed: code={}, err={}", code, e.getMessage());
            return LookupResult.miss();
        }
    }

    public LinkMeta get(String code) {
        return lookup(code).meta();
    }

    public void put(LinkMeta meta) {
        if (meta == null || meta.code() == null || meta.code().isBlank()) {
            return;
        }
        try {
            String raw = objectMapper.writeValueAsString(meta);
            redis.opsForValue().set(key(meta.code()), raw, Duration.ofSeconds(properties.getRedirect().getCacheTtlSeconds()));
        } catch (Exception e) {
            // 缓存写入失败不应影响主链路
            log.debug(
                    "cache write failed: code={}, tenantId={}, linkId={}, err={}",
                    meta == null ? null : meta.code(),
                    meta == null ? null : meta.tenantId(),
                    meta == null ? null : meta.id(),
                    e.getMessage()
            );
        }
    }

    public void markNotFound(String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        long ttlSeconds = properties == null || properties.getRedirect() == null
                ? 0
                : properties.getRedirect().getNotFoundCacheTtlSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        try {
            redis.opsForValue().set(key(code), NOT_FOUND_SENTINEL, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            // 负缓存写入失败不应影响主链路
            log.debug("cache write not-found failed: code={}, err={}", code, e.getMessage());
        }
    }

    public void evict(String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        try {
            redis.delete(key(code));
        } catch (Exception e) {
            log.debug("cache evict failed: code={}, err={}", code, e.getMessage());
        }
    }

    private static String key(String code) {
        return PREFIX + code;
    }
}
