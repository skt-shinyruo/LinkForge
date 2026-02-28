package com.linkforge.edge.web.error;

public class EdgeBusinessException extends RuntimeException {

    private final EdgeErrorCode errorCode;

    public EdgeBusinessException(EdgeErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public EdgeBusinessException(EdgeErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public EdgeErrorCode getErrorCode() {
        return errorCode;
    }
}

