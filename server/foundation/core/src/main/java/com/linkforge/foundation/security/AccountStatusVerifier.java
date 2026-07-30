package com.linkforge.foundation.security;

/**
 * 在凭据解析后确认租户和用户仍可使用的跨上下文端口。
 *
 * <p>JWT 签名有效不代表主体仍有效：实现必须检查禁用状态，并在提供 tokenVersion 的重载中比较当前版本，
 * 以支持密码重置等撤销场景。失败应以统一认证/授权异常向上传播，调用方不应把缓存未命中解释为有效。</p>
 */
public interface AccountStatusVerifier {

    /** 验证租户存在且处于启用状态，供 API Key 等没有用户 ID 的主体使用。 */
    void requireActiveTenant(long tenantId);

    /** 验证用户和租户处于启用状态，不执行 tokenVersion 比较，仅供兼容调用方使用。 */
    void requireActiveUserAndTenant(long userId, long tenantId);

    /** 验证用户、租户状态和 JWT 中的 tokenVersion 都仍然匹配。 */
    void requireActiveUserAndTenant(long userId, long tenantId, int tokenVersion);
}
