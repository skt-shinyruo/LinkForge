package com.linkforge.shortlink.infrastructure.redirect;

import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RedirectCacheSyncAdapter implements RedirectCacheSyncPort {

    private static final Logger log = LoggerFactory.getLogger(RedirectCacheSyncAdapter.class);

    private final LinkCachePort linkCache;

    public RedirectCacheSyncAdapter(LinkCachePort linkCache) {
        this.linkCache = linkCache;
    }

    @Override
    public void evict(String code) {
        if (linkCache.tryEvict(code)) {
            return;
        }
        log.debug("redirect cache evict failed: code={}", code);
    }
}
