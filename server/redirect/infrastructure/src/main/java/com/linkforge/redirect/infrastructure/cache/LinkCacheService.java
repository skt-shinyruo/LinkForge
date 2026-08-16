package com.linkforge.redirect.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkCachePort.LookupResult;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.foundation.config.RedirectProperties;
import com.linkforge.foundation.observability.OperationalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * {@link LinkCachePort} 的 Redis 三态缓存实现。
 *
 * <p>正缓存保存 {@link LinkMeta} JSON，负缓存保存固定 sentinel {@code __lf_not_found__}。读取故障、
 * 反序列化失败和没有 key 都映射为 {@code MISS}，绝不可映射为负缓存命中；这样 RedirectService 会回源
 * Shortlink，而不会把基础设施故障暴露为 404。</p>
 *
 * <p>写入与驱逐都是 best-effort 优化副作用：失败不抛给跳转主链路，Shortlink 的失效 outbox 负责最终
 * 重试。key 保留 code 大小写；host 存在时使用小写、去端口的 host-scoped key。</p>
 */
@Service
public class LinkCacheService implements LinkCachePort {

    private static final Logger log = LoggerFactory.getLogger(LinkCacheService.class);
    private static final String PREFIX = "link:code:";
    private static final String HOST_PREFIX = "link:host:";
    private static final String NOT_FOUND_SENTINEL = "__lf_not_found__";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RedirectProperties redirectProperties;
    private final OperationalMetrics metrics;

    public LinkCacheService(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            RedirectProperties redirectProperties,
            OperationalMetrics metrics
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.redirectProperties = redirectProperties;
        this.metrics = metrics == null ? OperationalMetrics.noop() : metrics;
    }

    /**
     * 读取给定 host + code 的三态缓存结果。
     *
     * <p>host 将在本实现中规范化为小写且去端口；code 由调用方验证后原样保留，以维持大小写敏感短码
     * 的缓存隔离。</p>
     */
    @Override
    public LookupResult lookup(String host, String code) {
        if (code == null || code.isBlank()) {
            return LookupResult.miss();
        }

        String raw;
        try {
            raw = redis.opsForValue().get(key(host, code));
        } catch (Exception e) {
            metrics.increment("linkforge.redirect.cache.lookups", "result", "error");
            // 缓存异常必须降级为 MISS，让主链路同步回源。
            log.debug("cache read failed: host={}, code={}, err={}", host, code, e.getMessage());
            return LookupResult.miss();
        }
        if (raw == null) {
            metrics.increment("linkforge.redirect.cache.lookups", "result", "miss");
            return LookupResult.miss();
        }
        if (NOT_FOUND_SENTINEL.equals(raw)) {
            metrics.increment("linkforge.redirect.cache.lookups", "result", "negative_hit");
            return LookupResult.negativeHit();
        }
        try {
            metrics.increment("linkforge.redirect.cache.lookups", "result", "hit");
            return LookupResult.hit(objectMapper.readValue(raw, LinkMeta.class));
        } catch (Exception e) {
            metrics.increment("linkforge.redirect.cache.lookups", "result", "deserialize_error");
            // 坏值不能等同于不存在；尽力清理后返回 MISS。
            try {
                redis.delete(key(host, code));
            } catch (Exception ex) {
                log.debug("cache delete failed after deserialize error: host={}, code={}, err={}", host, code, ex.getMessage());
            }
            log.debug("cache deserialize failed: host={}, code={}, err={}", host, code, e.getMessage());
            return LookupResult.miss();
        }
    }

    /**
     * 尝试写入指定 host + code 的正缓存。
     *
     * <p>无效元数据视为无需写入的成功，Redis 或 JSON 失败返回 {@code false} 并由上层决定是否重试。</p>
     */
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
            metrics.increment("linkforge.redirect.cache.operations", "operation", "write", "result", "failure");
            // 缓存写入失败不应影响主链路；outbox 会在后续变更时处理失效。
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

    /**
     * 为指定 host + code 尽力写入短 TTL 负缓存。
     *
     * <p>只有 Shortlink 权威读正常返回空时才允许调用本方法；TTL 非正时显式关闭负缓存。</p>
     */
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
            metrics.increment("linkforge.redirect.cache.operations", "operation", "negative_write", "result", "failure");
            // 负缓存写入失败只增加回源次数，不能影响本次查询结论。
            log.debug("cache write not-found failed: host={}, code={}, err={}", host, code, e.getMessage());
        }
    }

    /**
     * 尝试驱逐指定 host + code 的 key，重复调用安全。
     */
    @Override
    public boolean tryEvict(String host, String code) {
        if (code == null || code.isBlank()) {
            return true;
        }
        try {
            redis.delete(key(host, code));
            return true;
        } catch (Exception e) {
            metrics.increment("linkforge.redirect.cache.operations", "operation", "evict", "result", "failure");
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
