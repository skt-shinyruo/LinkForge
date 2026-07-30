package com.linkforge.shortlink.application;

import com.linkforge.foundation.context.UserActor;

import java.time.LocalDateTime;

/**
 * 短链更新、归档、恢复和删除用例边界。
 *
 * <p>本接口不定义外层事务；每个命令处理器拥有自己的事务边界，并在同一事务内维护聚合、领域事件和缓存失效
 * outbox。归档、恢复和删除只接收租户 ID，调用方必须先完成租户管理员授权。更新会使用用户主体执行
 * {@link ShortLinkUserAccess} 校验，普通用户只能修改自己创建的无应用短链。</p>
 */
public interface ShortLinkLifecycleUseCase {

    /**
     * 归档短链；重复归档按命令幂等语义返回当前状态。
     *
     * @param tenantId 已授权租户
     * @param linkId 短链 ID
     * @return 归档后的短链视图
     */
    LinkDto archive(long tenantId, long linkId);

    /**
     * 恢复已归档短链；重复恢复按命令幂等语义返回当前状态。
     *
     * @param tenantId 已授权租户
     * @param linkId 短链 ID
     * @return 恢复后的短链视图
     */
    LinkDto restore(long tenantId, long linkId);

    /**
     * 物理删除已经归档的短链；未归档时返回 {@code DELETE_REQUIRES_ARCHIVE}。
     *
     * <p>处理器按聚合版本执行条件删除，并发版本变化返回 {@code LINK_STALE_WRITE}；
     * 调用方必须已完成租户管理员授权。</p>
     *
     * @param tenantId 已授权租户
     * @param linkId 短链 ID
     */
    void delete(long tenantId, long linkId);

    /**
     * 按用户访问范围执行部分更新；应用短链目标地址变化可能转为审批申请而非立即写入。
     *
     * @param tenantId 必须与主体和短链归属一致的租户
     * @param linkId 短链 ID
     * @param req 部分更新参数
     * @param actor 已认证用户主体
     * @param requestedAt 审批申请时间，调用方应传入 UTC 墙钟时间
     * @return 更新后的视图，或带待审批信息的当前视图
     */
    LinkDto update(long tenantId, long linkId, UpdateLinkRequest req, UserActor actor, LocalDateTime requestedAt);
}
