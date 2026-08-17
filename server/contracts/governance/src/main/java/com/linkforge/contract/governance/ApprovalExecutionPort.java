package com.linkforge.contract.governance;

import java.time.LocalDateTime;

/**
 * Governance 在审批通过后同步调用的跨上下文执行端口。
 *
 * <p>官方 Governance 服务会先完成申请人、状态、自审批、权限矩阵和 payload 前置校验，找出支持操作的执行器，
 * 并要求至多一个匹配项，然后用条件更新抢占 {@code PENDING_APPROVAL}。零个匹配执行器不是错误：请求会稳定停在
 * {@code APPROVED}；多个匹配执行器在状态抢占前作为装配错误拒绝。实现不得假设仅靠本接口就能保证唯一注册。</p>
 *
 * <p>在官方流程中 {@link #execute(ApprovalExecutionRequest, LocalDateTime)} 与审批状态推进和批准审计处于同一个
 * Spring 事务。抛出的异常会使参与该事务的本地写入回滚；已经发生的 HTTP、消息或缓存等事务外副作用无法回滚，
 * 因此本端口不提供 exactly-once。执行器必须用 {@link ApprovalExecutionRequest#id()}、资源版本、唯一键或等价
 * 的业务机制承受事务回滚后的重试。</p>
 */
public interface ApprovalExecutionPort {

    /**
     * 判断本执行器是否支持指定的稳定操作类别。
     *
     * <p>该方法用于执行器选择，应保持无副作用且只依赖稳定配置/类型判断。Governance 会为非空操作调用它；某操作
     * 在运行时只能有零个或一个返回 {@code true} 的执行器，重复匹配会使审批在状态变更前失败。</p>
     *
     * @param operation 待选择的操作类别
     * @return 当且仅当本执行器能够验证并执行该操作时返回 {@code true}
     */
    boolean supports(SensitiveOperation operation);

    /**
     * 执行已批准操作。
     *
     * <p>实现必须首先确认 {@code request.operation()} 与自身支持的操作一致，再按该操作解释 snapshot 的
     * type/version 和可空规则。它必须以 {@code request.tenantId()} 查询当前资源，验证应用范围、资源状态和
     * 操作前置条件；存在 before snapshot 时还必须做陈旧状态或等效的乐观并发校验。目标地址变更需要比较
     * before 地址。</p>
     *
     * <p>任何未完成的业务执行都应抛出异常，不能吞掉后仍返回成功，否则 Governance 会把请求标为
     * {@code EXECUTED}。成功返回只表示本次调用完成，并不替执行器声明外部副作用的 exactly-once；重试防护
     * 必须由实现和其资源存储承担。</p>
     *
     * @param request Governance 发布的不可变请求快照；直接调用本方法时调用方同样必须保证其租户和快照不变量
     * @param executedAt 执行发生的时间，按 UTC {@link LocalDateTime} 解释；官方流程传入批准时使用的同一时间
     * @throws RuntimeException snapshot 非法、资源不再满足前置条件、并发冲突或执行失败时应向上传播，以便当前本地
     *                          审批事务回滚
     */
    void execute(ApprovalExecutionRequest request, LocalDateTime executedAt);
}
