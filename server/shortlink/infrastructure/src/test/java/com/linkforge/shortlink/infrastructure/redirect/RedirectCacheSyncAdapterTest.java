package com.linkforge.shortlink.infrastructure.redirect;

import com.linkforge.contract.platform.DomainHostnameLookupPort;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.foundation.config.CoreProperties;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedirectCacheSyncAdapterTest {

    @Test
    void evict_shouldClearBaseHostCacheForUnscopedLinks() {
        LinkCachePort linkCache = mock(LinkCachePort.class);
        DomainHostnameLookupPort domainHostnameLookupPort = mock(DomainHostnameLookupPort.class);
        CoreProperties coreProperties = mock(CoreProperties.class);
        when(coreProperties.getBaseUrl()).thenReturn(" https://Go.Example.Test/base-path ");
        when(linkCache.tryEvict("abc123")).thenReturn(true);
        when(linkCache.tryEvict("go.example.test", "abc123")).thenReturn(true);
        RedirectCacheSyncAdapter adapter = new RedirectCacheSyncAdapter(
                linkCache,
                domainHostnameLookupPort,
                coreProperties
        );

        adapter.evict(22L, null, "abc123");

        verify(linkCache).tryEvict("abc123");
        verify(coreProperties).getBaseUrl();
        verify(linkCache).tryEvict("go.example.test", "abc123");
        verifyNoInteractions(domainHostnameLookupPort);
    }
}
