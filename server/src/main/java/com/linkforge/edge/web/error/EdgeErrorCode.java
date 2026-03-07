package com.linkforge.edge.web.error;

public enum EdgeErrorCode {

    // Common (mirror API numeric codes for now; Edge may diverge later)
    BAD_REQUEST(40000, "请求参数错误"),
    FORBIDDEN(40300, "无权限"),
    NOT_FOUND(40400, "资源不存在"),
    TOO_MANY_REQUESTS(42900, "请求过于频繁"),
    INTERNAL_ERROR(50000, "服务内部错误"),

    // Redirect
    LINK_DISABLED(41001, "短链已禁用"),
    LINK_EXPIRED(41002, "短链已过期"),
    LINK_NOT_FOUND(40410, "短链不存在");

    private final int code;
    private final String defaultMessage;

    EdgeErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}

