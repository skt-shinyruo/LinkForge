package com.linkforge.governance;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.governance.application.ApprovalRequestSummaryResult;
import com.linkforge.governance.application.AuditLogSummaryResult;
import com.linkforge.governance.application.GovernancePageResult;
import com.linkforge.governance.application.GovernanceService;
import com.linkforge.governance.application.port.ApprovalRepository;
import com.linkforge.governance.domain.ApprovalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class GovernanceKeysetPaginationIntegrationTest extends GovernancePersistenceIntegrationTestSupport {

    private static final long TENANT_ID = 82301L;
    private static final long OTHER_TENANT_ID = 82302L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.parse("2026-08-15T10:00:00");

    @Autowired
    GovernanceService governanceService;

    @Autowired
    ApprovalRepository approvalRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, TENANT_ID);
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, OTHER_TENANT_ID);
        jdbcTemplate.update("DELETE FROM audit_logs WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT_ID);
        jdbcTemplate.update("DELETE FROM approval_requests WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT_ID);
    }

    @Test
    void approvalCursor_shouldNotDuplicateSkipOrCrossTenantWhenNewerRowIsInserted() {
        insertApproval(105L, TENANT_ID, "PENDING_APPROVAL", CREATED_AT, "payload-105");
        insertApproval(104L, TENANT_ID, "PENDING_APPROVAL", CREATED_AT, "payload-104");
        insertApproval(103L, TENANT_ID, "PENDING_APPROVAL", CREATED_AT, "payload-103");
        insertApproval(102L, TENANT_ID, "APPROVED", CREATED_AT.minusSeconds(1), "filtered");
        insertApproval(999L, OTHER_TENANT_ID, "PENDING_APPROVAL", CREATED_AT, "other-tenant");

        GovernancePageResult<ApprovalRequestSummaryResult> first = governanceService.listRequests(
                TENANT_ID,
                actor(),
                ApprovalStatus.PENDING_APPROVAL,
                2,
                null
        );
        insertApproval(110L, TENANT_ID, "PENDING_APPROVAL", CREATED_AT.plusSeconds(1), "concurrent-newer");
        GovernancePageResult<ApprovalRequestSummaryResult> second = governanceService.listRequests(
                TENANT_ID,
                actor(),
                ApprovalStatus.PENDING_APPROVAL,
                2,
                first.nextCursor()
        );

        assertThat(first.items()).extracting(ApprovalRequestSummaryResult::id).containsExactly(105L, 104L);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.items()).extracting(ApprovalRequestSummaryResult::id).containsExactly(103L);
        assertThat(second.hasMore()).isFalse();
        assertThat(approvalRepository.findByTenantIdAndId(TENANT_ID, 105L).orElseThrow().afterSnapshot())
                .isEqualTo("payload-105");
    }

    @Test
    void auditCursor_shouldCombineTenantAndFiltersWithStableSameTimeOrdering() {
        insertAudit(205L, TENANT_ID, "APPROVE_REQUEST", "approval_request", CREATED_AT, "large-before", "large-after");
        insertAudit(204L, TENANT_ID, "APPROVE_REQUEST", "approval_request", CREATED_AT, "large-before", "large-after");
        insertAudit(203L, TENANT_ID, "APPROVE_REQUEST", "approval_request", CREATED_AT, "large-before", "large-after");
        insertAudit(202L, TENANT_ID, "SUBMIT_REQUEST", "approval_request", CREATED_AT.minusSeconds(1), null, "filtered-action");
        insertAudit(201L, TENANT_ID, "APPROVE_REQUEST", "application", CREATED_AT.minusSeconds(1), null, "filtered-resource");
        insertAudit(999L, OTHER_TENANT_ID, "APPROVE_REQUEST", "approval_request", CREATED_AT, null, "other-tenant");

        GovernancePageResult<AuditLogSummaryResult> first = governanceService.listAuditLogs(
                TENANT_ID,
                actor(),
                "APPROVE_REQUEST",
                "approval_request",
                2,
                null
        );
        insertAudit(210L, TENANT_ID, "APPROVE_REQUEST", "approval_request", CREATED_AT.plusSeconds(1), null, "newer");
        GovernancePageResult<AuditLogSummaryResult> second = governanceService.listAuditLogs(
                TENANT_ID,
                actor(),
                "APPROVE_REQUEST",
                "approval_request",
                2,
                first.nextCursor()
        );

        assertThat(first.items()).extracting(AuditLogSummaryResult::id).containsExactly(205L, 204L);
        assertThat(second.items()).extracting(AuditLogSummaryResult::id).containsExactly(203L);
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    void schema_shouldInstallBoundedKeysetIndexes() {
        assertThat(indexExists("approval_requests", "idx_approval_requests_tenant_created_id")).isTrue();
        assertThat(indexExists("approval_requests", "idx_approval_requests_tenant_status_created_id")).isTrue();
        assertThat(indexExists("audit_logs", "idx_audit_logs_tenant_created_id")).isTrue();
        assertThat(indexExists("audit_logs", "idx_audit_logs_tenant_action_created_id")).isTrue();
        assertThat(indexExists("audit_logs", "idx_audit_logs_tenant_resource_created_id")).isTrue();
        assertThat(indexExists("audit_logs", "idx_audit_logs_tenant_action_resource_created_id")).isTrue();
    }

    private UserActor actor() {
        return new UserActor(TENANT_ID, 7L, "admin@example.test", Set.of("TENANT_ADMIN"));
    }

    private void insertApproval(long id, long tenantId, String status, LocalDateTime createdAt, String payload) {
        jdbcTemplate.update(
                """
                        INSERT INTO approval_requests (
                            id, tenant_id, operation_type, target_application_id, requested_by_user_id,
                            requested_by_email, status, before_snapshot, after_snapshot, created_at
                        ) VALUES (?, ?, 'PUBLIC_LINK_DESTINATION_CHANGE', 99, 7, 'requester@example.test', ?, 'before', ?, ?)
                        """,
                id,
                tenantId,
                status,
                payload,
                createdAt
        );
    }

    private void insertAudit(
            long id,
            long tenantId,
            String actionType,
            String resourceType,
            LocalDateTime createdAt,
            String beforeSnapshot,
            String afterSnapshot
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO audit_logs (
                            id, tenant_id, actor_user_id, actor_email, action_type, resource_type,
                            resource_id, request_id, before_snapshot, after_snapshot, created_at
                        ) VALUES (?, ?, 7, 'admin@example.test', ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                tenantId,
                actionType,
                resourceType,
                String.valueOf(id),
                id,
                beforeSnapshot,
                afterSnapshot,
                createdAt
        );
    }

    private boolean indexExists(String table, String index) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(DISTINCT index_name)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = ?
                          AND index_name = ?
                        """,
                Integer.class,
                table,
                index
        );
        return count != null && count == 1;
    }
}
