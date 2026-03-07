package com.linkforge.app.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.contract.api.ApiResponse;
import com.linkforge.contract.api.AppErrorCode;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.foundation.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 统一错误响应写入器：供 Filter/EntryPoint/AccessDeniedHandler 等非 Controller 场景复用。
 *
 * <p>约束：响应结构必须与 {@link ApiResponse} / {@link GlobalExceptionHandler} 一致，并尽量包含 requestId。</p>
 */
@Component
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, int httpStatus, AppErrorCode errorCode) throws IOException {
        write(response, httpStatus, errorCode, errorCode.getDefaultMessage());
    }

    public void write(HttpServletResponse response, int httpStatus, AppErrorCode errorCode, String message) throws IOException {
        if (response == null) {
            return;
        }

        String requestId = RequestId.get();
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
            response.setHeader(RequestIdFilter.HEADER_REQUEST_ID, requestId);
        }

        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Void> body = ApiResponse.error(errorCode.getCode(), message, requestId);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
