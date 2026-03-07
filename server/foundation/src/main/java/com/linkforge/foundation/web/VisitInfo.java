package com.linkforge.foundation.web;

import java.util.Map;

/**
 * 业务层可使用的“访问上下文”最小信息集。
 *
 * <p>约束：不依赖 Servlet API，便于在非 HTTP 场景复用与测试。</p>
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
