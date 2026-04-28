package com.linkforge.platform.domain;

public class DomainAuthorizationException extends RuntimeException {

    private final Reason reason;

    public DomainAuthorizationException(Reason reason) {
        super(reason == null ? null : reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        DEDICATED_DOMAIN_MISMATCH,
        SHARED_DOMAIN_NOT_AUTHORIZED
    }
}
