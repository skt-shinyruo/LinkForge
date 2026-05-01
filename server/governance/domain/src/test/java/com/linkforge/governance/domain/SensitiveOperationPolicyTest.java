package com.linkforge.governance.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveOperationPolicyTest {

    @Test
    void requiresApproval_shouldRecognizeSensitiveOperations() {
        SensitiveOperationPolicy policy = new SensitiveOperationPolicy();

        assertThat(policy.requiresApproval(SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE)).isTrue();
        assertThat(policy.requiresApproval(null)).isFalse();
    }
}
