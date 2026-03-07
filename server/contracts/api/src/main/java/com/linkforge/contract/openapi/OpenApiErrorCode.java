package com.linkforge.contract.openapi;

import com.linkforge.contract.api.AppErrorCode;

public enum OpenApiErrorCode implements AppErrorCode {

    API_KEY_INVALID(40110, "API Key 无效"),
    API_KEY_DISABLED(40310, "API Key 已禁用");

    private final int code;
    private final String defaultMessage;

    OpenApiErrorCode(int code, String defaultMessage) {
        this.code = code;
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
        return 401;
    }
}
