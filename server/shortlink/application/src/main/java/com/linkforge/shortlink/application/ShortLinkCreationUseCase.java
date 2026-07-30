package com.linkforge.shortlink.application;

import com.linkforge.foundation.context.ApiKeyActor;
import com.linkforge.foundation.context.UserActor;

/**
 * 短链创建用例边界。
 *
 * <p>面向认证主体的重载负责收敛应用作用域：用户仅可在自身允许的范围内创建，API Key 必须绑定应用。
 * 接受原始租户和创建者的重载供可信内部流程使用，调用方必须先完成身份授权。实际事务、应用/域名校验、
 * 发链额度预占、事件与缓存失效由创建命令处理器负责。</p>
 */
public interface ShortLinkCreationUseCase {

    /**
     * 按用户权限创建短链。
     *
     * @param actor 已认证用户主体
     * @param request 包含非可信外部作用域的创建请求
     * @return 已提交创建流程得到的短链视图
     */
    LinkDto createForUser(UserActor actor, ScopedCreateLinkRequest request);

    /**
     * 在 API Key 绑定应用内创建短链；未绑定应用或请求应用不匹配时拒绝。
     *
     * @param actor 已认证 API Key 主体
     * @param request 包含非可信外部作用域的创建请求
     * @return 已提交创建流程得到的短链视图
     */
    LinkDto createForApiKey(ApiKeyActor actor, ScopedCreateLinkRequest request);

    /**
     * 使用调用方提供的租户和创建者直接执行创建命令。
     *
     * @param tenantId 已授权的租户
     * @param createdBy 经可信调用方确认的创建主体
     * @param req 创建参数
     * @return 创建后的短链视图
     */
    LinkDto create(long tenantId, CreatedBy createdBy, CreateLinkRequest req);
}
