package com.linkforge.foundation.runtime.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 将副作用（如写缓存、发事件）延后到事务提交之后执行。
 *
 * <p>动机：避免 {@code @Transactional} 事务回滚时副作用无法回滚，导致 DB 与外部系统（Redis 等）不一致。</p>
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    /**
     * 在事务提交后执行 action；如果当前线程没有活跃事务同步，则直接执行。
     *
     * <p>说明：本方法不捕获 action 异常；对于缓存类副作用建议 action 内部自行吞错降级。</p>
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
