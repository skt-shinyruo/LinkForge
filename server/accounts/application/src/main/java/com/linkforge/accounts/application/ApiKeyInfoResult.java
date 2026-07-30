package com.linkforge.accounts.application;

import java.time.LocalDateTime;

/**
 * 不含 secret 的 API Key 管理视图。
 *
 * <p>{@code lastUsedAt} 可为 {@code null}，且是按配置节流、允许写入失败的近似审计时间；
 * {@code createdAt} 与 {@code lastUsedAt} 均以 UTC 解释，类型本身不携带偏移量。</p>
 */
public record ApiKeyInfoResult(long id, Long applicationId, String name, String status, LocalDateTime lastUsedAt, LocalDateTime createdAt) {
}
