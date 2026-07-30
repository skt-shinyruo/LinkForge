package com.linkforge.contract.platform;

/**
 * 为历史未分应用数据提供兼容绑定的窄口径发布契约。
 *
 * <p>它只服务于历史的 application/domain 双空数据回填或读取兼容，不能作为新业务的自动开通 API。
 * 实现应在租户边界内复用同一默认绑定；它不会修复已部分迁移或不一致的历史记录。</p>
 */
public interface LegacyApplicationProvisioningPort {

    /**
     * 返回租户默认绑定。
     *
     * <p>实现应尽量幂等：已存在绑定时必须返回同一配对。但并发首次创建的唯一键冲突或持久化故障仍可能以
     * 业务异常或运行时异常向上传播；调用方只能在新的业务事务中按自身幂等策略重试，不能把本方法当作无失败
     * 的读取，也不能在 catch 后假设默认绑定已经提交。</p>
     *
     * @param tenantId 历史数据所属租户，必须大于 {@code 0}
     * @return 同一租户内的默认 application/domain 配对
     */
    LegacyApplicationBindingView ensureLegacyDefaultBinding(long tenantId);
}
