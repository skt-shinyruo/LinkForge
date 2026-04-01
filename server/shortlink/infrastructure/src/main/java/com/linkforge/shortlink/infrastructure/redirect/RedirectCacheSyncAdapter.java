package com.linkforge.shortlink.infrastructure.redirect;

import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.platform.DomainHostnameLookupPort;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;

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

    @Override
    public void evict(long tenantId, Long domainId, String code) {
        if (linkCache.tryEvict(code)) {
            // keep going for host-aware keys
        } else {
            log.debug("redirect cache evict failed: code={}", code);
        }
        if (tenantId <= 0 || domainId == null || domainId <= 0 || code == null || code.isBlank()) {
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
