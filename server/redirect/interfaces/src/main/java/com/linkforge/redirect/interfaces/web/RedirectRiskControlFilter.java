package com.linkforge.redirect.interfaces.web;

import com.linkforge.redirect.application.risk.RedirectRiskControl;
import com.linkforge.redirect.interfaces.web.error.RedirectErrorResponseWriter;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.foundation.web.VisitInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /r/** 防滥用 Filter：构造 VisitInfo，并按配置执行基础风控（黑白名单 / 限流 / bot 策略）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RedirectRiskControlFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RedirectRiskControlFilter.class);

    public static final String ATTR_VISIT_INFO = "linkforge.visitInfo";
    private static final int DEFAULT_MAX_TRACKING_VALUE_LEN = 128;
    private static final int DEFAULT_MAX_UA_LEN = 512;
    private static final int MAX_UA_LEN_CAP = 2048;

    private final RedirectClientIpResolver clientIpResolver;
    private final RedirectRiskControl riskControl;
    private final RedirectErrorResponseWriter errorResponseWriter;
    private final AnalyticsProperties analyticsProperties;

    public RedirectRiskControlFilter(
            RedirectClientIpResolver clientIpResolver,
            RedirectRiskControl riskControl,
            RedirectErrorResponseWriter errorResponseWriter,
            AnalyticsProperties analyticsProperties
    ) {
        this.clientIpResolver = clientIpResolver;
        this.riskControl = riskControl;
        this.errorResponseWriter = errorResponseWriter;
        this.analyticsProperties = analyticsProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathWithinApp(request);
        return path == null || !path.startsWith("/r/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        VisitInfo visitInfo = resolveVisitInfo(request);
        request.setAttribute(ATTR_VISIT_INFO, visitInfo);

        String code = extractCode(request);
        RedirectRiskControl.Decision d = riskControl.check(visitInfo == null ? null : visitInfo.ip(),
                visitInfo == null ? null : visitInfo.userAgent(),
                code);

        if (d.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (d.retryAfterSeconds() != null && d.retryAfterSeconds() > 0) {
            response.setHeader("Retry-After", String.valueOf(d.retryAfterSeconds()));
        }

        log.info(
                "edge risk rejected: reason={}, code={}, requestId={}, ip={}",
                d.reasonCode(),
                code,
                RequestId.get(),
                visitInfo == null ? null : visitInfo.ip()
        );
        errorResponseWriter.write(request, response, d.httpStatus(), d.errorCode(), d.message());
    }

    private VisitInfo resolveVisitInfo(HttpServletRequest request) {
        String ip = clientIpResolver.resolveClientIp(request);
        int maxUaLen = resolveMaxUserAgentLength();
        String ua = truncateHeader(request == null ? null : request.getHeader("User-Agent"), maxUaLen);
        // Referer / language only affect analytics dimensions; cap them to avoid overlong headers
        String referer = truncateHeader(request == null ? null : request.getHeader("Referer"), 2048);
        String acceptLanguage = truncateHeader(request == null ? null : request.getHeader("Accept-Language"), 256);
        Map<String, String> trackingParams = extractTrackingParams(request);
        return new VisitInfo(ip, ua, referer, acceptLanguage, trackingParams);
    }

    private Map<String, String> extractTrackingParams(HttpServletRequest request) {
        if (request == null) {
            return Map.of();
        }
        Map<String, String[]> params = request.getParameterMap();
        if (params == null || params.isEmpty()) {
            return Map.of();
        }

        List<String> allowlist = analyticsProperties == null
                ? null
                : analyticsProperties.getTrackingParamAllowlist();
        if (allowlist == null || allowlist.isEmpty()) {
            // 安全默认：仅采集常见营销参数，避免把业务 query（token/账号等）带入统计链路
            allowlist = List.of("utm_*", "gclid", "fbclid");
        }

        Map<String, String> out = new LinkedHashMap<>();
        int maxTrackingValueLen = resolveMaxTrackingValueLength();
        int trackingScanLen = resolveTrackingScanLength(maxTrackingValueLen);
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            if (out.size() >= 20) {
                break;
            }
            String rawName = entry.getKey();
            if (rawName == null || rawName.isBlank()) {
                continue;
            }
            if (rawName.length() > 128) {
                continue;
            }
            String name = rawName.trim().toLowerCase(Locale.ROOT);
            if (!matchesAny(name, allowlist)) {
                continue;
            }

            String[] values = entry.getValue();
            if (values == null || values.length == 0) {
                continue;
            }
            String v = values[0];
            if (v == null) {
                continue;
            }
            String value = cleanInline(v, trackingScanLen);
            if (value == null || value.isBlank()) {
                continue;
            }
            if (maxTrackingValueLen > 0 && value.length() > maxTrackingValueLen) {
                value = value.substring(0, maxTrackingValueLen);
            }
            out.put(name, value);
        }
        return out.isEmpty() ? Map.of() : out;
    }

    private int resolveMaxTrackingValueLength() {
        int v = DEFAULT_MAX_TRACKING_VALUE_LEN;
        try {
            if (analyticsProperties != null
                    && analyticsProperties.getEvents() != null
                    && analyticsProperties.getEvents().getMaxTrackingValueLength() > 0) {
                v = analyticsProperties.getEvents().getMaxTrackingValueLength();
            }
        } catch (Exception ignored) {
            // ignore
        }
        // Safety bound: avoid accidentally huge value propagating into memory / logs.
        if (v <= 0) {
            return DEFAULT_MAX_TRACKING_VALUE_LEN;
        }
        return Math.min(v, 512);
    }

    private int resolveMaxUserAgentLength() {
        int v = DEFAULT_MAX_UA_LEN;
        try {
            if (analyticsProperties != null
                    && analyticsProperties.getEvents() != null
                    && analyticsProperties.getEvents().getMaxUserAgentLength() > 0) {
                v = analyticsProperties.getEvents().getMaxUserAgentLength();
            }
        } catch (Exception ignored) {
            // ignore
        }
        if (v <= 0) {
            return DEFAULT_MAX_UA_LEN;
        }
        return Math.min(v, MAX_UA_LEN_CAP);
    }

    private static int resolveTrackingScanLength(int maxTrackingValueLen) {
        int v = maxTrackingValueLen <= 0 ? DEFAULT_MAX_TRACKING_VALUE_LEN : maxTrackingValueLen;
        long scan = Math.max(v, (long) v * 4);
        if (scan > 2048) {
            scan = 2048;
        }
        return (int) scan;
    }

    private static String truncateHeader(String raw, int maxLen) {
        if (raw == null) {
            return null;
        }
        if (maxLen <= 0) {
            return raw;
        }
        return raw.length() <= maxLen ? raw : raw.substring(0, maxLen);
    }

    private static String cleanInline(String raw, int maxScanLen) {
        if (raw == null) {
            return null;
        }
        int limit = raw.length();
        if (maxScanLen > 0 && maxScanLen < limit) {
            limit = maxScanLen;
        }
        StringBuilder sb = new StringBuilder(Math.max(limit, 0));
        for (int i = 0; i < limit; i++) {
            char ch = raw.charAt(i);
            if (ch == '\n' || ch == '\r' || ch == '\t') {
                sb.append(' ');
            } else if (ch < 0x20) {
                sb.append(' ');
            } else {
                sb.append(ch);
            }
        }
        String t = sb.toString().trim();
        return t.isBlank() ? null : t;
    }

    private static boolean matchesAny(String name, List<String> patterns) {
        if (name == null || name.isBlank() || patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String p : patterns) {
            if (p == null || p.isBlank()) {
                continue;
            }
            String t = p.trim().toLowerCase(Locale.ROOT);
            if (t.isBlank()) {
                continue;
            }
            if (t.endsWith("*")) {
                String prefix = t.substring(0, t.length() - 1);
                if (!prefix.isBlank() && name.startsWith(prefix)) {
                    return true;
                }
            } else if (name.equals(t)) {
                return true;
            }
        }
        return false;
    }

    private static String extractCode(HttpServletRequest request) {
        String path = pathWithinApp(request);
        if (path == null) {
            return null;
        }
        // 仅用于风控维度，不做业务校验；实际解析仍以 Controller 的 path variable 为准
        if (!path.startsWith("/r/")) {
            return null;
        }
        String rest = path.substring("/r/".length());
        if (rest.isBlank()) {
            return null;
        }
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    private static String pathWithinApp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return null;
        }
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isBlank() && uri.startsWith(ctx)) {
            return uri.substring(ctx.length());
        }
        return uri;
    }
}
