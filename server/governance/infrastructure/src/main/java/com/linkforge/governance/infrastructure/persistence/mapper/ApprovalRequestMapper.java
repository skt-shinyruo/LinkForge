package com.linkforge.governance.infrastructure.persistence.mapper;

import com.linkforge.governance.infrastructure.persistence.entity.ApprovalRequestEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ApprovalRequestMapper {

    int insert(ApprovalRequestEntity entity);

    ApprovalRequestEntity findByTenantIdAndId(@Param("tenantId") long tenantId, @Param("requestId") long requestId);

    List<ApprovalRequestEntity> listByTenantId(@Param("tenantId") long tenantId);

    int markApprovedIfPending(
            @Param("tenantId") long tenantId,
            @Param("requestId") long requestId,
            @Param("approverUserId") long approverUserId,
            @Param("approverEmail") String approverEmail,
            @Param("decisionReason") String decisionReason,
            @Param("decidedAt") LocalDateTime decidedAt
    );

    int markExecutedIfApproved(
            @Param("tenantId") long tenantId,
            @Param("requestId") long requestId,
            @Param("executedAt") LocalDateTime executedAt
    );
}
