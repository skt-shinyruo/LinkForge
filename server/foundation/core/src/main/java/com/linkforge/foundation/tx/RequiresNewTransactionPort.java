package com.linkforge.foundation.tx;

/**
 * 在独立事务中执行动作的端口。
 *
 * <p>实现通常挂起外层事务，以 {@code REQUIRES_NEW} 提交或回滚动作；两个事务没有原子提交关系。动作异常
 * 应向上传播，调用方必须决定是否影响外层流程，不能把该端口当作分布式事务或可靠消息机制。</p>
 */
public interface RequiresNewTransactionPort {

    /** 在独立事务内运行 action。 */
    void run(Runnable action);
}
