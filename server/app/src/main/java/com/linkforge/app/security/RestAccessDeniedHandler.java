package com.linkforge.app.security;

import com.linkforge.app.api.error.ApiErrorResponseWriter;
import com.linkforge.contract.api.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 将 Spring Security 的已认证但无权限场景输出为统一 JSON 403。
 *
 * <p>不向客户端回显被拒绝的具体授权表达式或资源信息，避免辅助权限枚举。</p>
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorResponseWriter errorResponseWriter;

    public RestAccessDeniedHandler(ApiErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    /** 写出统一 {@code FORBIDDEN} 响应。 */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        errorResponseWriter.write(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN);
    }
}
