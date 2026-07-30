package com.linkforge.foundation.security;

/**
 * API Key 认证边界向安全过滤器输出的稳定失败分类。
 *
 * <p>异常消息只包含枚举名，不包含传入凭据、哈希或存储细节。HTTP 层可将 {@link ApiKeyAuthenticationFailure}
 * 映射为公开错误码，但不能据此暴露 Key 是否存在以外的敏感诊断。</p>
 */
public class ApiKeyAuthenticationException extends RuntimeException {

    private final ApiKeyAuthenticationFailure failure;

    /** 空分类按 {@link ApiKeyAuthenticationFailure#INVALID} 收敛，避免认证失败路径产生空状态。 */
    public ApiKeyAuthenticationException(ApiKeyAuthenticationFailure failure) {
        super(failure == null ? null : failure.name());
        this.failure = failure == null ? ApiKeyAuthenticationFailure.INVALID : failure;
    }

    /** 返回可供安全适配层转换的失败分类。 */
    public ApiKeyAuthenticationFailure failure() {
        return failure;
    }
}
