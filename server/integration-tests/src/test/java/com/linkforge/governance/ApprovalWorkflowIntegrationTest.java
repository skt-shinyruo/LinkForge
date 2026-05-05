package com.linkforge.governance;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.contract.governance.ApplicationQuotaIncreaseApprovalPayload;
import com.linkforge.contract.governance.ApprovalPayloadCodec;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.governance.application.ApprovalRequestResult;
import com.linkforge.governance.application.GovernanceService;
import com.linkforge.governance.application.SubmitApprovalRequest;
import com.linkforge.governance.domain.ApprovalStatus;
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
        ApprovalRequestResult request = governanceService.submitRequest(
                TENANT_ID,
                new SubmitApprovalRequest(
                        SensitiveOperationType.APPLICATION_QUOTA_INCREASE,
                        9001L,
                        null,
                        quotaPayload(50_000L, 1_000_000L),
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
        ApprovalRequestResult withinCeiling = governanceService.submitRequest(
                TENANT_ID,
                new SubmitApprovalRequest(
                        SensitiveOperationType.APPLICATION_QUOTA_INCREASE,
                        9002L,
                        null,
                        quotaPayload(50_000L, 1_000_000L),
                        requester,
                        LocalDateTime.now(ZoneOffset.UTC)
                )
        );
        ApprovalRequestResult aboveCeiling = governanceService.submitRequest(
                TENANT_ID,
                new SubmitApprovalRequest(
                        SensitiveOperationType.APPLICATION_QUOTA_INCREASE,
                        9003L,
                        null,
                        quotaPayload(250_000L, 1_000_000L),
                        requester,
                        LocalDateTime.now(ZoneOffset.UTC)
                )
        );

        UserActor approver = new UserActor(TENANT_ID, 203L, "approver@example.com", Set.of("TENANT_ADMIN"));
        authenticateAsTenantAdmin(TENANT_ID, 203L, "approver@example.com");
        ApprovalRequestResult approved = governanceService.approveRequest(
                TENANT_ID,
                withinCeiling.id(),
                "within ceiling",
                approver,
                LocalDateTime.now(ZoneOffset.UTC)
        );
        assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED);

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
        ApprovalRequestResult request = governanceService.submitRequest(
                TENANT_ID,
                new SubmitApprovalRequest(
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
        ApprovalRequestResult approved = governanceService.approveRequest(
                TENANT_ID,
                request.id(),
                "platform approve",
                platformApprover,
                LocalDateTime.now(ZoneOffset.UTC)
        );
        assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED);

        Long auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE request_id = ?", Long.class, request.id());
        assertThat(auditCount).isEqualTo(2L);
    }

    @Test
    void approved_request_should_not_be_approved_again_or_write_duplicate_audit() {
        UserActor requester = new UserActor(TENANT_ID, 207L, "requester-duplicate@example.com", Set.of("TENANT_ADMIN"));
        authenticateAsTenantAdmin(TENANT_ID, 207L, "requester-duplicate@example.com");
        ApprovalRequestResult request = governanceService.submitRequest(
                TENANT_ID,
                new SubmitApprovalRequest(
                        SensitiveOperationType.APPLICATION_QUOTA_INCREASE,
                        9004L,
                        null,
                        quotaPayload(50_000L, 1_000_000L),
                        requester,
                        LocalDateTime.now(ZoneOffset.UTC)
                )
        );

        UserActor firstApprover = new UserActor(TENANT_ID, 208L, "first-approver@example.com", Set.of("TENANT_ADMIN"));
        ApprovalRequestResult approved = governanceService.approveRequest(
                TENANT_ID,
                request.id(),
                "first",
                firstApprover,
                LocalDateTime.now(ZoneOffset.UTC)
        );

        UserActor secondApprover = new UserActor(TENANT_ID, 209L, "second-approver@example.com", Set.of("TENANT_ADMIN"));
        assertThatThrownBy(() -> governanceService.approveRequest(
                TENANT_ID,
                request.id(),
                "second",
                secondApprover,
                LocalDateTime.now(ZoneOffset.UTC)
        ))
                .hasMessageContaining("审批请求状态已变化");

        assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED);
        Long approveAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE request_id = ? AND action_type = 'APPROVE_REQUEST'",
                Long.class,
                request.id()
        );
        Long winningApprover = jdbcTemplate.queryForObject(
                "SELECT approver_user_id FROM approval_requests WHERE id = ?",
                Long.class,
                request.id()
        );
        assertThat(approveAuditCount).isEqualTo(1L);
        assertThat(winningApprover).isEqualTo(208L);
    }

    private static String quotaPayload(long monthlyLinkLimit, long monthlyClickLimit) {
        return ApprovalPayloadCodec.write(ApplicationQuotaIncreaseApprovalPayload.v1(
                monthlyLinkLimit,
                monthlyClickLimit
        ));
    }
}
