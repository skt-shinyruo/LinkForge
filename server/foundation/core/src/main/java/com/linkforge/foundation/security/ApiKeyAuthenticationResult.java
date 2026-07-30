package com.linkforge.foundation.security;

/**
 * API Key 校验成功后交给安全过滤器的最小身份结果。
 *
 * <p>实现返回成功结果时必须已验证 secret、启用状态和租户；当前新建 Key 必须绑定应用，
 * {@code applicationId} 的可空类型仅保留给历史兼容路径，消费者不得自行扩大范围。</p>
 */
public record ApiKeyAuthenticationResult(long tenantId, Long applicationId, long apiKeyId) {
}
