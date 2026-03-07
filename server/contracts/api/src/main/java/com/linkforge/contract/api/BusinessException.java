package com.linkforge.contract.api;

public class BusinessException extends RuntimeException {

    private final AppErrorCode errorCode;

    public BusinessException(AppErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(AppErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AppErrorCode getErrorCode() {
        return errorCode;
    }
}
