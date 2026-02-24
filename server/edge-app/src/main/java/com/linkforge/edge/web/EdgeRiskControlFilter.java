package com.linkforge.edge.web;

import com.linkforge.edge.risk.EdgeRiskControl;
import com.linkforge.platform.config.AppProperties;
import com.linkforge.platform.api.ApiErrorResponseWriter;
import com.linkforge.platform.web.RequestId;
import com.linkforge.platform.web.VisitInfo;
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
public class EdgeRiskControlFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(EdgeRiskControlFilter.class);

    public static final String ATTR_VISIT_INFO = "linkforge.visitInfo";

    private final EdgeClientIpResolver clientIpResolver;
    private final EdgeRiskControl riskControl;
    private final ApiErrorResponseWriter errorResponseWriter;
    private final AppProperties properties;

    public EdgeRiskControlFilter(
            EdgeClientIpResolver clientIpResolver,
            EdgeRiskControl riskControl,
            ApiErrorResponseWriter errorResponseWriter,
            AppProperties properties
    ) {
        this.clientIpResolver = clientIpResolver;
        this.riskControl = riskControl;
        this.errorResponseWriter = errorResponseWriter;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request == null ? null : request.getRequestURI();
        return uri == null || !uri.startsWith("/r/");
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
        EdgeRiskControl.Decision d = riskControl.check(visitInfo == null ? null : visitInfo.ip(),
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
        errorResponseWriter.write(response, d.httpStatus(), d.errorCode(), d.message());
    }

    private VisitInfo resolveVisitInfo(HttpServletRequest request) {
        String ip = clientIpResolver.resolveClientIp(request);
        String ua = request == null ? null : request.getHeader("User-Agent");
        String referer = request == null ? null : request.getHeader("Referer");
        String acceptLanguage = request == null ? null : request.getHeader("Accept-Language");
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

        List<String> allowlist = properties == null
                || properties.getAnalytics() == null
                ? List.of()
                : properties.getAnalytics().getTrackingParamAllowlist();
        if (allowlist == null || allowlist.isEmpty()) {
            // 安全默认：仅采集常见营销参数，避免把业务 query（token/账号等）带入统计链路
            allowlist = List.of("utm_*", "gclid", "fbclid");
        }

        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            if (out.size() >= 20) {
                break;
            }
            String rawName = entry.getKey();
            if (rawName == null || rawName.isBlank()) {
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
            String value = v.trim();
            if (value.isBlank()) {
                continue;
            }
            out.put(name, value);
        }
        return out.isEmpty() ? Map.of() : out;
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
        String uri = request == null ? null : request.getRequestURI();
        if (uri == null) {
            return null;
        }
        // 仅用于风控维度，不做业务校验；实际解析仍以 Controller 的 path variable 为准
        if (!uri.startsWith("/r/")) {
            return null;
        }
        String rest = uri.substring("/r/".length());
        if (rest.isBlank()) {
            return null;
        }
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }
}
