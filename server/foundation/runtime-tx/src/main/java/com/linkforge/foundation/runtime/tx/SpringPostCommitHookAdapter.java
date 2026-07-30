package com.linkforge.foundation.runtime.tx;

import com.linkforge.foundation.runtime.tx.AfterCommit;
import com.linkforge.foundation.tx.PostCommitHookPort;
import org.springframework.stereotype.Component;

/**
 * 把框架无关的提交后端口适配到 Spring 事务同步。
 *
 * <p>无活跃事务时动作会同步执行；事务回滚时不执行。调用方应把动作设计为可重复，并为需要可靠恢复的
 * 副作用另外写 durable outbox。</p>
 */
@Component
public final class SpringPostCommitHookAdapter implements PostCommitHookPort {

    @Override
    public void run(Runnable action) {
        AfterCommit.run(action);
    }
}
