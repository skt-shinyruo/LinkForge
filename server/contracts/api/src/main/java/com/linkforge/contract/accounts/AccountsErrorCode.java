package com.linkforge.contract.accounts;

import com.linkforge.contract.api.AppErrorCode;

public enum AccountsErrorCode implements AppErrorCode {

    EMAIL_ALREADY_EXISTS(40101, 400, "邮箱已存在"),
    INVALID_CREDENTIALS(40102, 401, "账号或密码错误"),
    TENANT_DISABLED(40301, 403, "租户已禁用"),
    USER_DISABLED(40302, 403, "用户已禁用");

    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    AccountsErrorCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
