package com.linkforge.governance.domain;

public record ApprovalDecisionReason(String value) {

    public ApprovalDecisionReason {
        if (value != null) {
            value = value.trim();
            if (value.isBlank()) {
                value = null;
            }
        }
    }

    public static ApprovalDecisionReason of(String raw) {
        return new ApprovalDecisionReason(raw);
    }
}
