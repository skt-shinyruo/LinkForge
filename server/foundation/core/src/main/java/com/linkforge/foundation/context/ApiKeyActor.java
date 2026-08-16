package com.linkforge.foundation.context;

/**
 * 由 OpenAPI API Key 认证得到的调用方 actor。
 *
 * <p>{@code applicationId} 为 API Key 的绑定应用；当前有效 Key 应有非空值，保留 {@code null} 只是为了
 * 兼容历史存储模型，调用方不得把它扩展为任意应用范围。API Key 原文不属于 actor，不能被记录或回传。</p>
 */
public record ApiKeyActor(long tenantId, long apiKeyId, Long applicationId) {
}
