package com.linkforge.foundation.web;

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
        String rid = sanitizeRequestId(request == null ? null : request.getHeader(HEADER_REQUEST_ID));
        if (rid == null) {
            rid = newRequestId();
        }

        RequestId.set(rid);
        MDC.put("requestId", rid);
        response.setHeader(HEADER_REQUEST_ID, rid);

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
        String t = raw.trim();
        if (t.isBlank()) {
            return null;
        }
        if (t.length() > MAX_REQUEST_ID_LENGTH) {
            return null;
        }
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
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
        return t;
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
