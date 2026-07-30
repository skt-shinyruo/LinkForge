package com.linkforge.foundation.tx;

/**
 * 在当前业务事务成功提交后执行轻量本地副作用的端口。
 *
 * <p>无活跃事务时实现可同步执行动作；已提交后进程崩溃、回调异常或外部系统异常都不会回滚数据库，故该端口
 * 不能作为可靠投递保证。需要最终恢复的副作用必须另有 durable outbox/重试机制。</p>
 */
public interface PostCommitHookPort {

    /** 注册或立即执行动作；动作应幂等且自行定义异常处理策略。 */
    void run(Runnable action);
}
