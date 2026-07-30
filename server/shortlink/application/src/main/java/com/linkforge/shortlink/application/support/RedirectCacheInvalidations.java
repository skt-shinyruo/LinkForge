package com.linkforge.shortlink.application.support;

import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.shortlink.application.port.RedirectCacheInvalidationOutboxPort;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;

/**
 * 编排短链写事务后的跳转缓存失效。
 *
 * <p>正确性路径是事务内持久化 outbox；after-commit 驱逐只用于缩短旧缓存可见窗口。两条路径故意都执行，
 * 即使快路径成功，outbox worker 后续仍会幂等重放并把记录标记完成。</p>
 */
public final class RedirectCacheInvalidations {

    private RedirectCacheInvalidations() {
    }

    /**
     * 先在当前业务事务中登记失效意图，再注册提交后的 best-effort 驱逐。
     *
     * <p>{@link RedirectCacheInvalidationOutboxPort#enqueue(long, Long, String)} 同步执行且异常不被吞掉，
     * 从而阻止缺少失效意图的业务提交。只有登记成功后才注册 hook；事务回滚时 hook 不应执行。提交后
     * {@link RedirectCacheSyncPort#evict(long, Long, String)} 的运行时异常会被忽略，因为此时业务状态已经
     * 提交，durable outbox 负责最终重试。若当前没有活跃事务，hook 适配器可能立即执行，因此调用方仍须
     * 负责提供正确事务边界。</p>
     *
     * @param outbox 持久化失效意图的事务端口
     * @param postCommitHookPort 提交后副作用注册端口
     * @param redirectCacheSync 幂等缓存驱逐端口
     * @param tenantId 短链所属租户
     * @param domainId 域名 ID；{@code null} 表示历史无域名 scope
     * @param code 待失效的短码
     */
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
                // 可靠性由持久化 outbox 重试路径承担，提交后驱逐只保留 best-effort 语义。
            }
        });
    }
}
