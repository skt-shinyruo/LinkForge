package com.linkforge.contract.shortlink;

import com.linkforge.contract.api.AppErrorCode;

public enum ShortLinkErrorCode implements AppErrorCode {

    CODE_ALREADY_EXISTS(40901, "短码已存在"),
    LINK_STALE_WRITE(40902, "短链已被其他请求修改，请刷新后重试"),
    LINK_DISABLED(41001, "短链已禁用"),
    LINK_EXPIRED(41002, "短链已过期"),
    LINK_NOT_FOUND(40410, "短链不存在"),
    INVALID_URL(40010, "URL 不合法");

    private final int code;
    private final String defaultMessage;

    ShortLinkErrorCode(int code, String defaultMessage) {
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
        return code / 100;
    }
}
