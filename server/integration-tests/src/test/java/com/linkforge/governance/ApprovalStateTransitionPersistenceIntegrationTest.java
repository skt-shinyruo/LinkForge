package com.linkforge.governance;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.governance.application.port.ApprovalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ApprovalStateTransitionPersistenceIntegrationTest extends GovernancePersistenceIntegrationTestSupport {

    private static final long TENANT_ID = 82101L;
    private static final long OTHER_TENANT_ID = 82102L;
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-04-01T12:00:00");

    @Autowired
    ApprovalRepository approvalRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpTenantsAndCleanRows() {
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, TENANT_ID);
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, OTHER_TENANT_ID);
        jdbcTemplate.update("DELETE FROM audit_logs WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT_ID);
        jdbcTemplate.update("DELETE FROM approval_requests WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT_ID);
    }

    @Test
    void markApprovedIfPending_shouldUpdateOnlyPendingRowForSameTenant() {
        insertApproval(9101L, TENANT_ID, "PENDING_APPROVAL");
        insertApproval(9102L, OTHER_TENANT_ID, "PENDING_APPROVAL");

        boolean updated = approvalRepository.markApprovedIfPending(
                TENANT_ID,
                9101L,
                8L,
                "approver@example.com",
                "ok",
                NOW
        );
        boolean wrongTenantUpdated = approvalRepository.markApprovedIfPending(
                TENANT_ID,
                9102L,
                8L,
                "approver@example.com",
                "ok",
                NOW
        );

        assertThat(updated).isTrue();
        assertThat(wrongTenantUpdated).isFalse();
        assertThat(status(9101L)).isEqualTo("APPROVED");
        assertThat(approverUserId(9101L)).isEqualTo(8L);
        assertThat(executedAt(9101L)).isNull();
        assertThat(status(9102L)).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void markApprovedIfPending_shouldNotUpdateNonPendingRows() {
        insertApproval(9201L, TENANT_ID, "APPROVED");
        insertApproval(9202L, TENANT_ID, "EXECUTED");
        insertApproval(9203L, TENANT_ID, "REJECTED");
        insertApproval(9204L, TENANT_ID, "CANCELLED");
        insertApproval(9205L, TENANT_ID, "EXPIRED");

        assertThat(approvalRepository.markApprovedIfPending(TENANT_ID, 9201L, 8L, "approver@example.com", "ok", NOW)).isFalse();
        assertThat(approvalRepository.markApprovedIfPending(TENANT_ID, 9202L, 8L, "approver@example.com", "ok", NOW)).isFalse();
        assertThat(approvalRepository.markApprovedIfPending(TENANT_ID, 9203L, 8L, "approver@example.com", "ok", NOW)).isFalse();
        assertThat(approvalRepository.markApprovedIfPending(TENANT_ID, 9204L, 8L, "approver@example.com", "ok", NOW)).isFalse();
        assertThat(approvalRepository.markApprovedIfPending(TENANT_ID, 9205L, 8L, "approver@example.com", "ok", NOW)).isFalse();

        assertThat(status(9201L)).isEqualTo("APPROVED");
        assertThat(status(9202L)).isEqualTo("EXECUTED");
        assertThat(status(9203L)).isEqualTo("REJECTED");
        assertThat(status(9204L)).isEqualTo("CANCELLED");
        assertThat(status(9205L)).isEqualTo("EXPIRED");
    }

    @Test
    void markExecutedIfApproved_shouldUpdateOnlyApprovedRowForSameTenant() {
        insertApproval(9301L, TENANT_ID, "APPROVED");
        insertApproval(9302L, TENANT_ID, "PENDING_APPROVAL");
        insertApproval(9303L, OTHER_TENANT_ID, "APPROVED");

        boolean updated = approvalRepository.markExecutedIfApproved(TENANT_ID, 9301L, NOW);
        boolean pendingUpdated = approvalRepository.markExecutedIfApproved(TENANT_ID, 9302L, NOW);
        boolean wrongTenantUpdated = approvalRepository.markExecutedIfApproved(TENANT_ID, 9303L, NOW);

        assertThat(updated).isTrue();
        assertThat(pendingUpdated).isFalse();
        assertThat(wrongTenantUpdated).isFalse();
        assertThat(status(9301L)).isEqualTo("EXECUTED");
        assertThat(executedAt(9301L)).isEqualTo(NOW);
        assertThat(status(9302L)).isEqualTo("PENDING_APPROVAL");
        assertThat(status(9303L)).isEqualTo("APPROVED");
    }

    private void insertApproval(long requestId, long tenantId, String status) {
        jdbcTemplate.update(
                """
                        INSERT INTO approval_requests (
                            id,
                            tenant_id,
                            operation_type,
                            target_application_id,
                            requested_by_user_id,
                            requested_by_email,
                            status,
                            before_snapshot,
                            after_snapshot,
                            created_at
                        ) VALUES (?, ?, 'ANALYTICS_DETAIL_EXPORT', 2001, 7, 'requester@example.com', ?, NULL, 'snapshot', ?)
                        """,
                requestId,
                tenantId,
                status,
                NOW.minusHours(1)
        );
    }

    private String status(long requestId) {
        return jdbcTemplate.queryForObject("SELECT status FROM approval_requests WHERE id = ?", String.class, requestId);
    }

    private Long approverUserId(long requestId) {
        return jdbcTemplate.queryForObject("SELECT approver_user_id FROM approval_requests WHERE id = ?", Long.class, requestId);
    }

    private LocalDateTime executedAt(long requestId) {
        return jdbcTemplate.queryForObject("SELECT executed_at FROM approval_requests WHERE id = ?", LocalDateTime.class, requestId);
    }
}
