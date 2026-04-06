package com.linkforge.foundation.runtime.web;

import com.linkforge.foundation.web.RequestId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Runtime-owned servlet filter for request-id propagation.
 *
 * <p>This bean lives under {@code foundation.runtime.web} because it is part of the executable
 * HTTP runtime: it reads request headers, populates logging context, and writes response headers.
 * The shared-library side of {@code foundation} should expose only reusable types, not servlet beans.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    private static final int MAX_REQUEST_ID_LENGTH = 64;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = sanitizeRequestId(request == null ? null : request.getHeader(HEADER_REQUEST_ID));
        if (requestId == null) {
            requestId = newRequestId();
        }

        RequestId.set(requestId);
        MDC.put("requestId", requestId);
        response.setHeader(HEADER_REQUEST_ID, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
            RequestId.clear();
        }
    }

    static String sanitizeRequestId(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        if (trimmed.length() > MAX_REQUEST_ID_LENGTH) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            boolean ok = (ch >= '0' && ch <= '9')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z')
                    || ch == '-'
                    || ch == '_'
                    || ch == '.';
            if (!ok) {
                return null;
            }
        }
        return trimmed;
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
