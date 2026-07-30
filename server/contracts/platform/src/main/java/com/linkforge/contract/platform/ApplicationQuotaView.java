package com.linkforge.contract.platform;

/**
 * Platform 发布的应用月度额度视图。
 *
 * <p>“月”由消费方按 UTC 自然月解释。该 record 只描述配置快照，不代表本次请求已预留额度，也不
 * 提供并发计数能力；Shortlink 和 Redirect 必须分别通过各自的原子 reservation port 执行硬限制。</p>
 *
 * @param applicationId 已验证为当前租户 ACTIVE 应用的 ID，必须大于 {@code 0}
 * @param monthlyLinkLimit UTC 月发链上限；{@code <= 0} 表示该维度不限制
 * @param monthlyClickLimit UTC 月点击上限；{@code <= 0} 表示该维度不限制
 */
public record ApplicationQuotaView(
        long applicationId,
        long monthlyLinkLimit,
        long monthlyClickLimit
) {
}
