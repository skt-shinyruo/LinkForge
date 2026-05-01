package com.linkforge.governance.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalMatrixPolicyTest {

    private final ApprovalMatrixPolicy policy = new ApprovalMatrixPolicy();

    @Test
    void mayApprove_shouldAllowTenantAdminForNormalTenantScopedRequest() {
        ApprovalActor actor = new ApprovalActor(1L, 8L, "admin@example.com", Set.of("TENANT_ADMIN"));

        assertThat(policy.mayApprove(actor, request(SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE, "monthlyLinkLimit=1000")))
                .isTrue();
    }

    @Test
    void mayApprove_shouldRequirePlatformAdminForExternalDomainBinding() {
        ApprovalActor tenantAdmin = new ApprovalActor(1L, 8L, "admin@example.com", Set.of("TENANT_ADMIN"));
        ApprovalActor platformAdmin = new ApprovalActor(1L, 8L, "admin@example.com", Set.of("PLATFORM_ADMIN"));

        assertThat(policy.mayApprove(tenantAdmin, request(SensitiveOperationType.EXTERNAL_DOMAIN_BINDING, ""))).isFalse();
        assertThat(policy.mayApprove(platformAdmin, request(SensitiveOperationType.EXTERNAL_DOMAIN_BINDING, ""))).isTrue();
    }

    @Test
    void mayApprove_shouldRequirePlatformAdminForLargeQuotaIncrease() {
        ApprovalActor tenantAdmin = new ApprovalActor(1L, 8L, "admin@example.com", Set.of("TENANT_ADMIN"));

        assertThat(policy.mayApprove(
                tenantAdmin,
                request(SensitiveOperationType.APPLICATION_QUOTA_INCREASE, "monthlyLinkLimit=100001")
        )).isFalse();
    }

    private static ApprovalRequest request(SensitiveOperationType type, String afterSnapshot) {
        return new ApprovalRequest(
                1L,
                1L,
                type,
                10L,
                7L,
                "requester@example.com",
                ApprovalStatus.PENDING_APPROVAL,
                null,
                null,
                null,
                "",
                afterSnapshot,
                java.time.LocalDateTime.parse("2026-04-30T10:00:00"),
                null,
                null
        );
    }
}
