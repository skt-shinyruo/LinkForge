package com.linkforge.contract.api;

public enum ErrorCode {

    // 通用
    BAD_REQUEST(40000, "请求参数错误"),
    UNAUTHORIZED(40100, "未登录或凭证无效"),
    FORBIDDEN(40300, "无权限"),
    NOT_FOUND(40400, "资源不存在"),
    TOO_MANY_REQUESTS(42900, "请求过于频繁"),
    INTERNAL_ERROR(50000, "服务内部错误"),

    // IAM
    EMAIL_ALREADY_EXISTS(40101, "邮箱已存在"),
    INVALID_CREDENTIALS(40102, "账号或密码错误"),
    TENANT_DISABLED(40301, "租户已禁用"),
    USER_DISABLED(40302, "用户已禁用"),

    // ShortLink / Redirect
    CODE_ALREADY_EXISTS(40901, "短码已存在"),
    LINK_DISABLED(41001, "短链已禁用"),
    LINK_EXPIRED(41002, "短链已过期"),
    LINK_NOT_FOUND(40410, "短链不存在"),
    INVALID_URL(40010, "URL 不合法"),

    // OpenAPI
    API_KEY_INVALID(40110, "API Key 无效"),
    API_KEY_DISABLED(40310, "API Key 已禁用");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
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

