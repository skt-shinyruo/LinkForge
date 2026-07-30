package com.linkforge.foundation.runtime.tx;

import com.linkforge.foundation.tx.RequiresNewTransactionPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link RequiresNewTransactionPort} 的 Spring 实现。
 *
 * <p>每次调用都会挂起外层事务并以 {@code PROPAGATION_REQUIRES_NEW} 打开独立事务；动作成功时独立提交，
 * 动作抛出运行时异常时独立回滚并向调用方传播。它不提供跨事务原子性，不能用于替代 outbox 或分布式事务。</p>
 */
@Component
public final class SpringRequiresNewTransactionAdapter implements RequiresNewTransactionPort {

    private final TransactionTemplate template;

    public SpringRequiresNewTransactionAdapter(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** @throws RuntimeException action 抛出的运行时异常或事务基础设施异常 */
    @Override
    public void run(Runnable action) {
        template.executeWithoutResult(status -> action.run());
    }
}
