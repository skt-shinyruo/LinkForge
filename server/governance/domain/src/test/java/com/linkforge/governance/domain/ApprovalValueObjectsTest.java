package com.linkforge.governance.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalValueObjectsTest {

    @Test
    void approvalActor_shouldRequirePositiveIdentity() {
        assertThatThrownBy(() -> new ApprovalActor(1L, 0L, "admin@example.com", Set.of("TENANT_ADMIN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void approvalDecisionReason_shouldTrimBlankToNull() {
        assertThat(ApprovalDecisionReason.of(" ok ").value()).isEqualTo("ok");
        assertThat(ApprovalDecisionReason.of(" ").value()).isNull();
    }

    @Test
    void approvalSnapshot_shouldParseMonthlyLinkLimit() {
        java.util.OptionalLong limit = ApprovalSnapshot
                .of("applicationId=1, monthlyLinkLimit=2000, monthlyClickLimit=3")
                .monthlyLinkLimit();

        assertThat(limit.isPresent()).isTrue();
        assertThat(limit.getAsLong()).isEqualTo(2000L);
    }
}
