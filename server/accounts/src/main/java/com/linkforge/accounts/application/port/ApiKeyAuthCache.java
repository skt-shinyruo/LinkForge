package com.linkforge.accounts.application.port;

public interface ApiKeyAuthCache {

    Entry read(long apiKeyId);

    void putActive(long apiKeyId, long tenantId, Long applicationId, String secretDigest, long ttlSeconds);

    void putDisabled(long apiKeyId, long tenantId, Long applicationId, long ttlSeconds);

    void evict(long apiKeyId);

    LastUsedTokenResult tryAcquireLastUsedToken(long apiKeyId, long intervalSeconds);

    void releaseLastUsedToken(long apiKeyId);

    record Entry(long tenantId, Long applicationId, String status, String secretDigest) {
    }

    enum LastUsedTokenResult {
        ACQUIRED,
        NOT_ACQUIRED,
        CACHE_UNAVAILABLE
    }
}
