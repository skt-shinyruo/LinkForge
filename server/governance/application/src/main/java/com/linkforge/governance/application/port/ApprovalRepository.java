package com.linkforge.governance.application.port;

import com.linkforge.governance.domain.ApprovalRequest;

import java.util.List;
import java.util.Optional;

public interface ApprovalRepository {

    void insert(ApprovalRequest request);

    Optional<ApprovalRequest> findByTenantIdAndId(long tenantId, long requestId);

    List<ApprovalRequest> listByTenantId(long tenantId);

    void updateDecision(
            long requestId,
            String status,
            long approverUserId,
            String approverEmail,
            String decisionReason,
            java.time.LocalDateTime decidedAt,
            java.time.LocalDateTime executedAt
    );
}
