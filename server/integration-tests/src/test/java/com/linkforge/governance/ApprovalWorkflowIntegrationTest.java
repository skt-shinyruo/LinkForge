package com.linkforge.governance;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.governance.application.GovernanceService;
import com.linkforge.governance.domain.SensitiveOperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ApprovalWorkflowIntegrationTest extends GovernancePersistenceIntegrationTestSupport {

    private static final long TENANT_ID = 82001L;

    @Autowired
    GovernanceService governanceService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpTenant() {
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, TENANT_ID);
    }

    @Test
    void requester_should_not_be_able_to_approve_own_sensitive_request() {
        UserActor requester = new UserActor(TENANT_ID, 201L, "requester@example.com", Set.of("TENANT_ADMIN"));
        authenticateAsTenantAdmin(TENANT_ID, 201L, "requester@example.com");
        GovernanceService.ApprovalRequestDto request = governanceService.submitRequest(
                TENANT_ID,
                new GovernanceService.SubmitApprovalRequest(
                        SensitiveOperationType.APPLICATION_QUOTA_INCREASE,
                        9001L,
                        null,
                        "monthlyLinkLimit=50000,monthlyClickLimit=1000000",
                        requester,
                        LocalDateTime.now(ZoneOffset.UTC)
                )
        );

        assertThatThrownBy(() -> governanceService.approveRequest(TENANT_ID, request.id(), "self approve", requester, LocalDateTime.now(ZoneOffset.UTC)))
                .hasMessageContaining("申请人与审批人不能是同一人");
    }

    @Test
    void tenant_admin_should_only_approve_quota_increase_within_tenant_ceiling() {
        UserActor requester = new UserActor(TENANT_ID, 202L, "requester@example.com", Set.of("TENANT_ADMIN"));
        authenticateAsTenantAdmin(TENANT_ID, 202L, "requester@example.com");
        GovernanceService.ApprovalRequestDto withinCeiling = governanceService.submitRequest(
                TENANT_ID,
                new GovernanceService.SubmitApprovalRequest(
                        SensitiveOperationType.APPLICATION_QUOTA_INCREASE,
                        9002L,
                        null,
                        "monthlyLinkLimit=50000,monthlyClickLimit=1000000",
                        requester,
                        LocalDateTime.now(ZoneOffset.UTC)
                )
        );
        GovernanceService.ApprovalRequestDto aboveCeiling = governanceService.submitRequest(
                TENANT_ID,
                new GovernanceService.SubmitApprovalRequest(
                        SensitiveOperationType.APPLICATION_QUOTA_INCREASE,
                        9003L,
                        null,
                        "monthlyLinkLimit=250000,monthlyClickLimit=1000000",
                        requester,
                        LocalDateTime.now(ZoneOffset.UTC)
                )
        );

        UserActor approver = new UserActor(TENANT_ID, 203L, "approver@example.com", Set.of("TENANT_ADMIN"));
        authenticateAsTenantAdmin(TENANT_ID, 203L, "approver@example.com");
        GovernanceService.ApprovalRequestDto approved = governanceService.approveRequest(
                TENANT_ID,
                withinCeiling.id(),
                "within ceiling",
                approver,
                LocalDateTime.now(ZoneOffset.UTC)
        );
        assertThat(approved.status()).isEqualTo(com.linkforge.governance.domain.ApprovalStatus.EXECUTED);

        assertThatThrownBy(() -> governanceService.approveRequest(
                TENANT_ID,
                aboveCeiling.id(),
                "too large",
                approver,
                LocalDateTime.now(ZoneOffset.UTC)
        ))
                .hasMessageContaining("超出租户管理员可审批的配额上限");
    }

    @Test
    void platform_admin_should_be_required_for_external_domain_binding() {
        UserActor requester = new UserActor(TENANT_ID, 204L, "requester@example.com", Set.of("TENANT_ADMIN"));
        authenticateAsTenantAdmin(TENANT_ID, 204L, "requester@example.com");
        GovernanceService.ApprovalRequestDto request = governanceService.submitRequest(
                TENANT_ID,
                new GovernanceService.SubmitApprovalRequest(
                        SensitiveOperationType.EXTERNAL_DOMAIN_BINDING,
                        null,
                        null,
                        "hostname=partner.example",
                        requester,
                        LocalDateTime.now(ZoneOffset.UTC)
                )
        );

        UserActor tenantApprover = new UserActor(TENANT_ID, 205L, "tenant-approver@example.com", Set.of("TENANT_ADMIN"));
        authenticateAsTenantAdmin(TENANT_ID, 205L, "tenant-approver@example.com");
        assertThatThrownBy(() -> governanceService.approveRequest(
                TENANT_ID,
                request.id(),
                "tenant approve",
                tenantApprover,
                LocalDateTime.now(ZoneOffset.UTC)
        ))
                .hasMessageContaining("外部域名绑定需平台管理员审批");

        UserActor platformApprover = new UserActor(TENANT_ID, 206L, "platform-approver@example.com", Set.of("PLATFORM_ADMIN"));
        authenticateAsPlatformAdmin(TENANT_ID, 206L, "platform-approver@example.com");
        GovernanceService.ApprovalRequestDto approved = governanceService.approveRequest(
                TENANT_ID,
                request.id(),
                "platform approve",
                platformApprover,
                LocalDateTime.now(ZoneOffset.UTC)
        );
        assertThat(approved.status()).isEqualTo(com.linkforge.governance.domain.ApprovalStatus.EXECUTED);

        Long auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE request_id = ?", Long.class, request.id());
        assertThat(auditCount).isEqualTo(2L);
    }
}
