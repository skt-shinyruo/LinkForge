package com.linkforge.foundation.security;

/**
 * OpenAPI API Key 的认证端口。
 *
 * <p>输入是未经信任的原始凭据，实现负责格式解析、持久化哈希比对、状态和应用绑定校验。调用方不得记录
 * 参数值，也不得将其放入错误响应或事件；认证失败应归一为 {@link ApiKeyAuthenticationException}。</p>
 */
public interface ApiKeyAuthenticator {

    /**
     * 验证原始 API Key 并返回可建立安全上下文的身份结果。
     *
     * @throws ApiKeyAuthenticationException 凭据无效或已禁用时抛出
     */
    ApiKeyAuthenticationResult authenticateApiKey(String apiKey);
}
