package com.linkforge.governance.application.port;

import com.linkforge.governance.domain.ApprovalRequest;

import java.util.List;
import java.util.Optional;

public interface ApprovalRepository {

    void insert(ApprovalRequest request);

    Optional<ApprovalRequest> findByTenantIdAndId(long tenantId, long requestId);

    List<ApprovalRequest> listByTenantId(long tenantId);

    boolean markApprovedIfPending(
            long tenantId,
            long requestId,
            long approverUserId,
            String approverEmail,
            String decisionReason,
            java.time.LocalDateTime decidedAt
    );

    boolean markExecutedIfApproved(
            long tenantId,
            long requestId,
            java.time.LocalDateTime executedAt
    );
}
