package com.linkforge.governance.domain;

public class SensitiveOperationPolicy {

    public boolean requiresApproval(SensitiveOperationType operationType) {
        return operationType != null;
    }
}
