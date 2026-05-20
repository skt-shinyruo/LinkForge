package com.linkforge.shortlink.application.port;

public interface RedirectCacheInvalidationOutboxPort {

    void enqueue(long tenantId, Long domainId, String code);
}
