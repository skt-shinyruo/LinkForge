package com.linkforge.foundation.web;

/**
 * Redirect 主链路交给风控和统计层的已清洗客户端上下文。
 *
 * <p>生产 HTTP 请求应由 Redirect 风控过滤器构造：{@code ip} 已经过可信代理规则解析，User-Agent 已限长。
 * 直接构造本类型不会重新验证这些输入，因此不得把未受信任的 forwarded header 直接传入。</p>
 */
public record VisitInfo(
        String ip,
        String userAgent
) {
}
