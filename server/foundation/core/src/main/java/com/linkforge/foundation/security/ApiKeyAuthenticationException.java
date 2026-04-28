package com.linkforge.foundation.security;

public class ApiKeyAuthenticationException extends RuntimeException {

    private final ApiKeyAuthenticationFailure failure;

    public ApiKeyAuthenticationException(ApiKeyAuthenticationFailure failure) {
        super(failure == null ? null : failure.name());
        this.failure = failure == null ? ApiKeyAuthenticationFailure.INVALID : failure;
    }

    public ApiKeyAuthenticationFailure failure() {
        return failure;
    }
}
