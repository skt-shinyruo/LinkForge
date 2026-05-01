package com.linkforge.governance.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditPolicyTest {

    @Test
    void requiredActionType_shouldNameApprovalLifecycleActions() {
        AuditPolicy policy = new AuditPolicy();

        assertThat(policy.requiredActionType(AuditPolicy.AuditAction.SUBMIT_REQUEST)).isEqualTo("SUBMIT_REQUEST");
        assertThat(policy.requiredResourceType()).isEqualTo("approval_request");
    }
}
