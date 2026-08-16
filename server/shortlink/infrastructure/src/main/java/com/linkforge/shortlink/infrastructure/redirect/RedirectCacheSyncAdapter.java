package com.linkforge.shortlink.infrastructure.redirect;

import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.platform.DomainHostnameLookupPort;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * 删除 Redirect 上下文维护的短链缓存键。
 *
 * <p>一次调用会先删除历史的纯短码键，再按域名删除当前 host-aware 键，并在命中 Legacy 域名映射时补删
 * 基础域名兼容键。多个删除动作不是原子的，中途失败会抛出异常交给 outbox 重试；已经删除的键再次删除
 * 必须视为成功，因此整个操作可安全重入。</p>
 *
 * <p>无法解析配置中的基础 URL 时跳过兼容键；指定 {@code domainId} 却查不到域名时也只能删除纯短码键。
 * 这些情况不伪造域名，依靠后续正常读取与 TTL 收敛残留的 host-aware 缓存。</p>
 */
@Component
public class RedirectCacheSyncAdapter implements RedirectCacheSyncPort {

    private static final Logger log = LoggerFactory.getLogger(RedirectCacheSyncAdapter.class);

    private final LinkCachePort linkCache;
    private final DomainHostnameLookupPort domainHostnameLookupPort;
    private final CoreProperties coreProperties;

    public RedirectCacheSyncAdapter(LinkCachePort linkCache, DomainHostnameLookupPort domainHostnameLookupPort, CoreProperties coreProperties) {
        this.linkCache = linkCache;
        this.domainHostnameLookupPort = domainHostnameLookupPort;
        this.coreProperties = coreProperties;
    }

    /**
     * 删除给定短链可能对应的所有已知缓存键。
     *
     * @throws IllegalStateException 任一底层缓存删除报告失败时抛出，以触发持久化重试
     */
    @Override
    public void evict(long tenantId, Long domainId, String code) {
        if (linkCache.tryEvict(null, code)) {
            // 纯短码键成功后仍需继续删除按主机隔离的键。
        } else {
            log.debug("redirect cache evict failed: code={}", code);
            throw new IllegalStateException("redirect cache evict failed: code=" + code);
        }
        if (tenantId <= 0 || code == null || code.isBlank()) {
            return;
        }
        if (domainId == null || domainId <= 0) {
            String baseHost = legacyCompatibilityHost(tenantId);
            if (baseHost != null) {
                evictHost(baseHost, code);
            }
            return;
        }
        domainHostnameLookupPort.findDomainHostname(tenantId, domainId).ifPresent(hostname -> {
            evictHost(hostname, code);
            String legacyCompatibilityHost = legacyCompatibilityHost(tenantId);
            if (legacyCompatibilityHost != null && hostname.equalsIgnoreCase(legacyDomainHostname(tenantId, legacyCompatibilityHost))) {
                evictHost(legacyCompatibilityHost, code);
            }
        });
    }

    private void evictHost(String host, String code) {
        if (linkCache.tryEvict(host, code)) {
            return;
        }
        log.debug("redirect cache evict failed: host={}, code={}", host, code);
        throw new IllegalStateException("redirect cache evict failed: host=" + host + ", code=" + code);
    }

    private String legacyCompatibilityHost(long tenantId) {
        String baseUrl = coreProperties == null ? null : coreProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(baseUrl.trim());
            return normalizeHost(uri.getHost());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String legacyDomainHostname(long tenantId, String baseHost) {
        return "legacy-" + tenantId + "." + baseHost;
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
