package com.linkforge.shortlink.application.port;

public interface RedirectCacheSyncPort {

    void evict(long tenantId, Long domainId, String code);
}
