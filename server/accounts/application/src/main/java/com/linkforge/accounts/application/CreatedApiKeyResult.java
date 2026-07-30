package com.linkforge.accounts.application;

/**
 * API Key 创建或轮换结果；{@code apiKey} 是不可恢复的单次可见明文，不应记录到日志或再次持久化。
 */
public record CreatedApiKeyResult(long id, String name, String apiKey) {
}
