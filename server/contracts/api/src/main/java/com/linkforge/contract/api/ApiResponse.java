package com.linkforge.contract.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/** LinkForge JSON API 的统一响应信封。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(int code, String message, T data, String requestId) {

    public static <T> ApiResponse<T> ok(T data, String requestId) {
        return new ApiResponse<>(0, "ok", data, requestId);
    }

    public static <T> ApiResponse<T> error(int code, String message, String requestId) {
        return new ApiResponse<>(code, message, null, requestId);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getRequestId() {
        return requestId;
    }
}
