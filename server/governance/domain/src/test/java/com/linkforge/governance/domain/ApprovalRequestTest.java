package com.linkforge.governance.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalRequestTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.parse("2026-04-28T10:00:00");
    private static final LocalDateTime DECIDED_AT = LocalDateTime.parse("2026-04-28T10:05:00");
    private static final LocalDateTime EXECUTED_AT = LocalDateTime.parse("2026-04-28T10:06:00");

    @Test
    void approve_shouldMovePendingRequestToApproved() {
        ApprovalRequest approved = pending().approve(8L, "approver@example.com", "ok", DECIDED_AT);

        assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approved.approverUserId()).isEqualTo(8L);
        assertThat(approved.approverEmail()).isEqualTo("approver@example.com");
        assertThat(approved.decisionReason()).isEqualTo("ok");
        assertThat(approved.decidedAt()).isEqualTo(DECIDED_AT);
        assertThat(approved.executedAt()).isNull();
    }

    @Test
    void approve_shouldRejectSelfApproval() {
        assertThatThrownBy(() -> pending().approve(7L, "requester@example.com", "ok", DECIDED_AT))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("reason")
                .isEqualTo(ApprovalDomainException.Reason.SELF_APPROVAL);
    }

    @Test
    void approve_shouldRejectNonPendingRequest() {
        ApprovalRequest alreadyApproved = pending().approve(8L, "approver@example.com", "ok", DECIDED_AT);

        assertThatThrownBy(() -> alreadyApproved.approve(9L, "other@example.com", "again", DECIDED_AT))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("reason")
                .isEqualTo(ApprovalDomainException.Reason.APPROVAL_NOT_PENDING);
    }

    @Test
    void markExecuted_shouldMoveApprovedRequestToExecuted() {
        ApprovalRequest approved = pending().approve(8L, "approver@example.com", "ok", DECIDED_AT);

        ApprovalRequest executed = approved.markExecuted(EXECUTED_AT);

        assertThat(executed.status()).isEqualTo(ApprovalStatus.EXECUTED);
        assertThat(executed.approverUserId()).isEqualTo(8L);
        assertThat(executed.decidedAt()).isEqualTo(DECIDED_AT);
        assertThat(executed.executedAt()).isEqualTo(EXECUTED_AT);
    }

    @Test
    void markExecuted_shouldRejectNonApprovedRequest() {
        assertThatThrownBy(() -> pending().markExecuted(EXECUTED_AT))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("reason")
                .isEqualTo(ApprovalDomainException.Reason.APPROVAL_NOT_APPROVED);
    }

    private static ApprovalRequest pending() {
        return new ApprovalRequest(
                501L,
                1L,
                SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE,
                2001L,
                7L,
                "requester@example.com",
                ApprovalStatus.PENDING_APPROVAL,
                null,
                null,
                null,
                "before",
                "after",
                CREATED_AT,
                null,
                null
        );
    }
}
