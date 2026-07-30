package com.linkforge.accounts.application;

/**
 * Accounts 内部的 API Key 认证结果。
 *
 * <p>认证成功时 {@code applicationId} 必定非 {@code null}；保留包装类型仅为兼容存储模型。</p>
 */
public record ApiKeyAuthResult(long tenantId, Long applicationId, long apiKeyId) {
}
