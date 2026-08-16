package com.linkforge.shortlink.infrastructure.redirect;

/**
 * 一条到期的缓存失效任务。
 *
 * <p>{@code generation} 标识当前失效 identity 的代际。每次重新入队都会原子递增该值，worker 只能用
 * 自己读取到的代际推进状态，避免旧 worker 覆盖更新一代的 durable intent。{@code attempts} 是当前代际
 * 此前失败次数，下一次失败时由任务递增。</p>
 */
public record RedirectCacheInvalidationOutboxRow(
        long id,
        long tenantId,
        Long domainId,
        String code,
        String status,
        long generation,
        int attempts
) {
}
