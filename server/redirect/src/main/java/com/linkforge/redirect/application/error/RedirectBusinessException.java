package com.linkforge.redirect.application.error;

public class RedirectBusinessException extends RuntimeException {

    private final RedirectErrorCode errorCode;

    public RedirectBusinessException(RedirectErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public RedirectBusinessException(RedirectErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RedirectErrorCode getErrorCode() {
        return errorCode;
    }
}
