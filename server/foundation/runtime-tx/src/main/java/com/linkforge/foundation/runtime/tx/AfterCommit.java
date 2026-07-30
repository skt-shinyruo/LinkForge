package com.linkforge.foundation.runtime.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 将本地副作用（如缓存失效）延后到事务提交之后执行。
 *
 * <p>动机是避免 {@code @Transactional} 回滚时外部副作用无法回滚。它不是可靠消息机制：进程在数据库提交后、
 * 回调执行前退出时动作会丢失，回调异常也不能回滚已提交的数据。因此影响正确性的副作用必须同时使用 durable
 * outbox 或具备可重试的补偿路径。</p>
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    /**
     * 在事务提交后执行 action；如果当前线程没有活跃事务同步，则直接执行。
     *
     * <p>动作在提交后于当前调用线程执行，异常原样传播给 Spring 的回调路径但不会回滚已经提交的数据。无事务
     * 或事务同步未激活时会立即同步执行，调用方不能据此假设动作一定异步。</p>
     */
    public static void run(Runnable action) {
        if (action == null) {
            return;
        }

        boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();
        boolean syncActive = TransactionSynchronizationManager.isSynchronizationActive();
        if (!txActive || !syncActive) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
