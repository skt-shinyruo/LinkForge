package com.linkforge.governance.domain;

public class ApprovalDomainException extends RuntimeException {

    private final Reason reason;

    public ApprovalDomainException(Reason reason) {
        super(reason == null ? null : reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        APPROVAL_NOT_PENDING,
        SELF_APPROVAL,
        APPROVAL_NOT_APPROVED
    }
}
