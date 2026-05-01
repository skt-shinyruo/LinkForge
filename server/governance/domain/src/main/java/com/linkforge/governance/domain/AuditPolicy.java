package com.linkforge.governance.domain;

public class AuditPolicy {

    public String requiredActionType(AuditAction action) {
        if (action == null) {
            throw new IllegalArgumentException("audit action must be provided");
        }
        return action.name();
    }

    public String requiredResourceType() {
        return "approval_request";
    }

    public enum AuditAction {
        SUBMIT_REQUEST,
        APPROVE_REQUEST
    }
}
