package com.linkforge.foundation.runtime.tx;

import com.linkforge.foundation.tx.RequiresNewTransactionPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public final class SpringRequiresNewTransactionAdapter implements RequiresNewTransactionPort {

    private final TransactionTemplate template;

    public SpringRequiresNewTransactionAdapter(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void run(Runnable action) {
        template.executeWithoutResult(status -> action.run());
    }
}
