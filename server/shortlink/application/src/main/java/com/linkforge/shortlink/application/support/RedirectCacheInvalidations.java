package com.linkforge.shortlink.application.support;

import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.shortlink.application.port.RedirectCacheInvalidationOutboxPort;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;

public final class RedirectCacheInvalidations {

    private RedirectCacheInvalidations() {
    }

    public static void enqueueAndRunAfterCommit(
            RedirectCacheInvalidationOutboxPort outbox,
            PostCommitHookPort postCommitHookPort,
            RedirectCacheSyncPort redirectCacheSync,
            long tenantId,
            Long domainId,
            String code
    ) {
        outbox.enqueue(tenantId, domainId, code);
        postCommitHookPort.run(() -> {
            try {
                redirectCacheSync.evict(tenantId, domainId, code);
            } catch (RuntimeException ignored) {
                // The durable outbox retry path owns reliability; after-commit eviction stays best-effort.
            }
        });
    }
}
