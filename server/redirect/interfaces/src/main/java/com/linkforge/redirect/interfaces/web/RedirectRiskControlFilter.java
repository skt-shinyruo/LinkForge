package com.linkforge.redirect.interfaces.web;

import com.linkforge.foundation.web.RequestId;
import com.linkforge.foundation.web.VisitInfo;
import com.linkforge.redirect.application.risk.RedirectRiskControl;
import com.linkforge.redirect.interfaces.web.error.RedirectErrorResponseWriter;
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

/** Applies redirect risk controls and carries only the bounded IP/UA context downstream. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RedirectRiskControlFilter extends OncePerRequestFilter {

    public static final String ATTR_VISIT_INFO = "linkforge.visitInfo";
    private static final int MAX_USER_AGENT_LENGTH = 512;
    private static final Logger log = LoggerFactory.getLogger(RedirectRiskControlFilter.class);

    private final RedirectClientIpResolver clientIpResolver;
    private final RedirectRiskControl riskControl;
    private final RedirectErrorResponseWriter errorResponseWriter;

    public RedirectRiskControlFilter(
            RedirectClientIpResolver clientIpResolver,
            RedirectRiskControl riskControl,
            RedirectErrorResponseWriter errorResponseWriter
    ) {
        this.clientIpResolver = clientIpResolver;
        this.riskControl = riskControl;
        this.errorResponseWriter = errorResponseWriter;
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
        RedirectRiskControl.Decision decision = riskControl.check(
                visitInfo == null ? null : visitInfo.ip(),
                visitInfo == null ? null : visitInfo.userAgent(),
                code
        );
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (decision.retryAfterSeconds() != null && decision.retryAfterSeconds() > 0) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        }
        log.info(
                "edge risk rejected: reason={}, code={}, requestId={}, ip={}",
                decision.reasonCode(), code, RequestId.get(), visitInfo == null ? null : visitInfo.ip()
        );
        errorResponseWriter.write(
                request, response, decision.httpStatus(), decision.errorCode(), decision.message()
        );
    }

    private VisitInfo resolveVisitInfo(HttpServletRequest request) {
        String ip = clientIpResolver.resolveClientIp(request);
        String ua = request == null ? null : request.getHeader("User-Agent");
        if (ua != null && ua.length() > MAX_USER_AGENT_LENGTH) {
            ua = ua.substring(0, MAX_USER_AGENT_LENGTH);
        }
        return new VisitInfo(ip, ua);
    }

    private static String extractCode(HttpServletRequest request) {
        String path = pathWithinApp(request);
        if (path == null || !path.startsWith("/r/")) {
            return null;
        }
        String rest = path.substring(3);
        int slash = rest.indexOf('/');
        String code = slash < 0 ? rest : rest.substring(0, slash);
        return code.isBlank() ? null : code;
    }

    private static String pathWithinApp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return null;
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
