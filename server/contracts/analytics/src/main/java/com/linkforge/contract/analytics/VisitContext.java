package com.linkforge.contract.analytics;

import java.util.Map;

/**
 * Published Language: redirect/edge 侧采集到的“访问上下文”最小信息集。
 *
 * <p>约束：不依赖 Servlet API，便于在非 HTTP 场景复用与测试。</p>
 */
public record VisitContext(
        String ip,
        String userAgent,
        String referer,
        String acceptLanguage,
        Map<String, String> trackingParams
) {

    public VisitContext {
        trackingParams = trackingParams == null ? Map.of() : Map.copyOf(trackingParams);
    }
}

