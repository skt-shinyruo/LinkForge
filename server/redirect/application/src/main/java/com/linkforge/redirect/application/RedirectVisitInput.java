package com.linkforge.redirect.application;

import java.util.Map;

/**
 * Redirect 传给 Analytics 的经过边界清洗的访问上下文。
 *
 * <p>IP、UA、Referer、语言和 tracking 参数由 Edge filter 限长、筛选后提供；本记录不负责再次信任
 * forwarded headers，也不保证 Analytics 已持久化。</p>
 */
public record RedirectVisitInput(
        String ip,
        String userAgent,
        String referer,
        String acceptLanguage,
        Map<String, String> trackingParams
) {
}
