package com.linkforge.redirect.application.risk;

/**
 * Redirect Edge 风控的窗口计数端口。
 *
 * <p>调用方依赖“递增并在首次创建时设置 TTL”的原子语义。端口实现故障允许抛异常，由
 * {@link RedirectRiskControl} 按 risk-control 配置决定 fail-open 或拒绝，不应在实现层伪造成功。</p>
 */
public interface RateLimiterPort {

    /**
     * 原子递增当前窗口的计数器，并在首次出现时设置 TTL。
     *
     * @param key 当前窗口的唯一 Redis key
     * @param ttlSeconds key 的存活秒数
     * @return 递增后的值；无效参数可返回 {@code 0}
     */
    long increment(String key, int ttlSeconds);
}
