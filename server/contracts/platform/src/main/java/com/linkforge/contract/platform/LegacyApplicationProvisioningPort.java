package com.linkforge.contract.platform;

/**
 * 为历史未分应用数据提供兼容绑定的窄口径发布契约。
 *
 * <p>它只服务于历史的 application/domain 双空数据回填或读取兼容，不能作为新业务的自动开通 API。
 * 实现应在租户边界内串行执行 get-or-reconcile，只返回满足 ACTIVE、归属、授权和当前配置要求的绑定。
 * 可修复的缺失或过期 policy/quota 在同一事务中补齐；停用、跨租户或错误绑定必须拒绝。</p>
 */
public interface LegacyApplicationProvisioningPort {

    /**
     * 返回租户默认绑定。
     *
     * <p>实现必须幂等：完整绑定重复调用返回同一配对，并发首次调用收敛到同一逻辑结果。持久化故障或
     * 不可安全修复的已有资源仍会以业务异常或运行时异常传播；调用方不能在 catch 后假设默认绑定已提交。</p>
     *
     * @param tenantId 历史数据所属租户，必须大于 {@code 0}
     * @return 同一租户内的默认 application/domain 配对
     */
    LegacyApplicationBindingView ensureLegacyDefaultBinding(long tenantId);
}
