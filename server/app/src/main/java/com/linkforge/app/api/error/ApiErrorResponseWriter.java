package com.linkforge.app.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.contract.api.ApiResponse;
import com.linkforge.contract.api.AppErrorCode;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.foundation.runtime.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 统一错误响应写入器，供 Filter、EntryPoint 和 AccessDeniedHandler 等非 Controller 场景复用。
 *
 * <p>响应结构必须与 {@link ApiResponse}/{@link GlobalExceptionHandler} 一致。正常 HTTP 请求的 requestId
 * 由 {@code RequestIdFilter} 提供；若调用发生在过滤器之外，本类会仅为当前响应生成并写回请求头的临时 ID，
 * 不会把该值安装到 ThreadLocal 或 MDC。</p>
 */
@Component
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 按错误码默认消息写出 JSON 错误体。 */
    public void write(HttpServletResponse response, int httpStatus, AppErrorCode errorCode) throws IOException {
        write(response, httpStatus, errorCode, errorCode.getDefaultMessage());
    }

    /**
     * 写出 JSON 错误体，不记录异常细节或凭据。
     *
     * <p>response 为 {@code null} 时保持 no-op，便于过滤器防御式调用。</p>
     */
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
