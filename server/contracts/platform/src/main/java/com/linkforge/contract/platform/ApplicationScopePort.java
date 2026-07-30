package com.linkforge.contract.platform;

import java.util.Optional;

/**
 * Platform 向其他上下文发布的应用、域名授权和额度查询契约。
 *
 * <p>所有方法都以 {@code tenantId} 作为不可绕过的边界，而不是只按全局 ID 查询。实现必须把不存在、
 * 禁用及跨租户资源统一转换为稳定业务失败；调用方不得以异常类型差异枚举其他租户的应用或域名。该端口
 * 只提供当前授权和配置事实，不会为消费方预留配额，也不保证随后资源状态不会被并发修改。</p>
 */
public interface ApplicationScopePort {

    /**
     * 要求租户内应用存在且为 ACTIVE。
     *
     * @param tenantId 当前已认证租户，必须大于 {@code 0}
     * @param applicationId 待验证应用 ID，必须大于 {@code 0}
     * @throws com.linkforge.contract.api.BusinessException 应用不存在、已禁用或不属于该租户时抛出
     */
    void requireApplicationExists(long tenantId, long applicationId);

    /**
     * 要求 ACTIVE 应用获准使用指定 ACTIVE 域名。
     *
     * <p>专属域名必须绑定到该应用；共享域名按 Platform 当前授权规则处理。该校验不是资源锁，成功返回后
     * 仍可能被后台操作解绑或禁用，跨事务长流程应在实际写入侧保持自己的约束。</p>
     *
     * @param tenantId 当前已认证租户，必须大于 {@code 0}
     * @param applicationId 待验证应用 ID，必须大于 {@code 0}
     * @param domainId 待验证域名 ID，必须大于 {@code 0}
     * @throws com.linkforge.contract.api.BusinessException 应用或域名不存在、非 ACTIVE 或未授权时抛出
     */
    void requireApplicationAndDomainAuthorized(long tenantId, long applicationId, long domainId);

    /**
     * 查询 ACTIVE 应用的额度配置。
     *
     * <p>空值只表示该 ACTIVE 应用尚无额度记录，当前 Shortlink/Redirect 消费方按“不限制”处理；它不表示
     * 应用不存在。返回 view 后，两个非正 limit 也分别表示不限制。结果是读取快照，不能用于并发硬额度
     * 判断，也不能据此推断任何 reservation 已发生。</p>
     *
     * @param tenantId 当前已认证租户，必须大于 {@code 0}
     * @param applicationId 待查询应用 ID，必须大于 {@code 0}
     * @return 有额度行时返回配置；ACTIVE 应用无额度行时返回空
     * @throws com.linkforge.contract.api.BusinessException 应用不存在、已禁用或不属于该租户时抛出
     */
    Optional<ApplicationQuotaView> findApplicationQuota(long tenantId, long applicationId);
}
