package com.linkforge.redirect.interfaces.web.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 非 HTML Redirect 请求的稳定错误响应体。
 *
 * <p>{@code code} 是协议级业务代码，{@code requestId} 用于与服务端日志关联；不要在 {@code message}
 * 放入异常堆栈、Redis key、目标 URL 或其他内部细节。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RedirectErrorResponse(
        int code,
        String message,
        String requestId
) {
}
