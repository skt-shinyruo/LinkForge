package com.linkforge.platform.domain;

import java.time.LocalDateTime;

/**
 * 应用级月度发链与点击额度快照。
 *
 * <p>额度按应用隔离，消费方以 UTC 自然月计算使用窗口。两个上限为非正数时均表示不限制；该 record
 * 不执行计数或预留，也不保证多请求并发下的原子性，实际额度占用由 Shortlink/Analytics 各自的
 * 额度端口完成。</p>
 *
 * @param applicationId 额度所属的应用 ID，同时也是持久化主键
 * @param monthlyLinkLimit UTC 自然月内允许创建的短链数，非正数表示不限制
 * @param monthlyClickLimit UTC 自然月内允许的点击数，非正数表示不限制
 * @param createdAt 创建时间，首次写入前可为 {@code null}
 * @param updatedAt 最近更新时间，首次写入前可为 {@code null}
 */
public record ApplicationQuota(
        long applicationId,
        long monthlyLinkLimit,
        long monthlyClickLimit,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
