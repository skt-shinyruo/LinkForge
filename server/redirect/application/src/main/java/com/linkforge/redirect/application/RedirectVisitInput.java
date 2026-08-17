package com.linkforge.redirect.application;

/**
 * Redirect 传给 Analytics 的经过边界清洗的客户端上下文。
 *
 * <p>IP 和 UA 由 Edge filter 限长并按可信代理规则解析；本记录不负责再次信任 forwarded headers，也不保证
 * Analytics 已持久化。</p>
 */
public record RedirectVisitInput(
        String ip,
        String userAgent
) {
}
