package com.linkforge.foundation.web;

import java.util.Map;

/**
 * Redirect 主链路交给业务和统计层的已清洗访问上下文。
 *
 * <p>该类型不依赖 Servlet API，便于在非 HTTP 场景复用与测试。生产 HTTP 请求应由 Redirect 风控过滤器
 * 构造：{@code ip} 已经过可信代理规则解析，header 字段和 tracking 参数已按各自上限/白名单清洗。直接构造
 * 本类型不会重新验证这些输入，因此不得把来自未受信任请求的 forwarded header 或任意 query 直接传入。</p>
 *
 * <p>IP、User-Agent、Referer 和语言都可能构成个人数据或敏感上下文，只能交给明确需要它们的风控/统计
 * 链路，不能写入公开响应。{@code trackingParams} 在构造时复制为不可变快照；{@code null} 统一表示空映射，
 * 其他标量字段保留调用方提供的 {@code null} 语义。</p>
 */
public record VisitInfo(
        String ip,
        String userAgent,
        String referer,
        String acceptLanguage,
        Map<String, String> trackingParams
) {

    public VisitInfo {
        trackingParams = trackingParams == null ? Map.of() : Map.copyOf(trackingParams);
    }
}
