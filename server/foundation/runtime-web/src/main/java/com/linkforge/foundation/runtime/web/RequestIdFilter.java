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
 * HTTP 请求关联 ID 的运行时过滤器。
 *
 * <p>过滤器只接受由字母、数字、{@code -}、{@code _}、{@code .} 组成且长度不超过 64 的
 * {@code X-Request-Id}；不符合约束的客户端值会被替换为服务端生成的 UUID。该 ID 仅用于日志、错误响应
 * 和排障关联，不是认证或授权凭据。</p>
 *
 * <p>在进入下游链路前，它同时写入响应头、{@link RequestId} 和 MDC；无论下游是否抛错，都会在
 * {@code finally} 中清理线程局部状态，避免线程池复用时串到下一请求。Servlet 依赖留在 runtime 包，
 * 共享层只暴露无框架的 {@link RequestId}。</p>
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

    /**
     * 规范化可由客户端传入的关联 ID。
     *
     * <p>返回 {@code null} 表示调用方必须生成新值；该方法不会截断超长值，防止攻击者利用截断制造
     * 难以区分的日志关联。</p>
     */
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
