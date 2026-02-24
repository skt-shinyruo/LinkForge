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

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public LinkCacheService(StringRedisTemplate redis, ObjectMapper objectMapper, AppProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public LinkMeta get(String code) {
        String raw;
        try {
            raw = redis.opsForValue().get(key(code));
        } catch (Exception e) {
            // 缓存异常：降级为未命中，让主链路回源
            log.debug("cache read failed: code={}, err={}", code, e.getMessage());
            return null;
        }
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, LinkMeta.class);
        } catch (Exception e) {
            // 缓存反序列化失败时，直接当作未命中并清理
            try {
                redis.delete(key(code));
            } catch (Exception ex) {
                log.debug("cache delete failed after deserialize error: code={}, err={}", code, ex.getMessage());
            }
            log.debug("cache deserialize failed: code={}, err={}", code, e.getMessage());
            return null;
        }
    }

    public void put(LinkMeta meta) {
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

    public void evict(String code) {
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
