package com.linkforge.governance.infrastructure.persistence;

import com.linkforge.governance.application.port.ApprovalRepository;
import com.linkforge.governance.domain.ApprovalRequest;
import com.linkforge.governance.domain.ApprovalStatus;
import com.linkforge.governance.domain.SensitiveOperationType;
import com.linkforge.governance.infrastructure.persistence.entity.ApprovalRequestEntity;
import com.linkforge.governance.infrastructure.persistence.mapper.ApprovalRequestMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class ApprovalRepositoryMybatisAdapter implements ApprovalRepository {

    private final ApprovalRequestMapper mapper;

    public ApprovalRepositoryMybatisAdapter(ApprovalRequestMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(ApprovalRequest request) {
        mapper.insert(toEntity(request));
    }

    @Override
    public Optional<ApprovalRequest> findByTenantIdAndId(long tenantId, long requestId) {
        return Optional.ofNullable(mapper.findByTenantIdAndId(tenantId, requestId)).map(this::toDomain);
    }

    @Override
    public List<ApprovalRequest> listByTenantId(long tenantId) {
        return mapper.listByTenantId(tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean markApprovedIfPending(
            long tenantId,
            long requestId,
            long approverUserId,
            String approverEmail,
            String decisionReason,
            LocalDateTime decidedAt
    ) {
        return mapper.markApprovedIfPending(
                tenantId,
                requestId,
                approverUserId,
                approverEmail,
                decisionReason,
                decidedAt
        ) > 0;
    }

    @Override
    public boolean markExecutedIfApproved(long tenantId, long requestId, LocalDateTime executedAt) {
        return mapper.markExecutedIfApproved(tenantId, requestId, executedAt) > 0;
    }

    private static ApprovalRequestEntity toEntity(ApprovalRequest request) {
        ApprovalRequestEntity entity = new ApprovalRequestEntity();
        entity.setId(request.id());
        entity.setTenantId(request.tenantId());
        entity.setOperationType(request.operationType().name());
        entity.setTargetApplicationId(request.targetApplicationId());
        entity.setRequestedByUserId(request.requestedByUserId());
        entity.setRequestedByEmail(request.requestedByEmail());
        entity.setStatus(request.status().name());
        entity.setApproverUserId(request.approverUserId());
        entity.setApproverEmail(request.approverEmail());
        entity.setDecisionReason(request.decisionReason());
        entity.setBeforeSnapshot(request.beforeSnapshot());
        entity.setAfterSnapshot(request.afterSnapshot());
        entity.setCreatedAt(request.createdAt());
        entity.setDecidedAt(request.decidedAt());
        entity.setExecutedAt(request.executedAt());
        return entity;
    }

    private ApprovalRequest toDomain(ApprovalRequestEntity entity) {
        return new ApprovalRequest(
                entity.getId(),
                entity.getTenantId(),
                SensitiveOperationType.valueOf(entity.getOperationType()),
                entity.getTargetApplicationId(),
                entity.getRequestedByUserId(),
                entity.getRequestedByEmail(),
                ApprovalStatus.valueOf(entity.getStatus()),
                entity.getApproverUserId(),
                entity.getApproverEmail(),
                entity.getDecisionReason(),
                entity.getBeforeSnapshot(),
                entity.getAfterSnapshot(),
                entity.getCreatedAt(),
                entity.getDecidedAt(),
                entity.getExecutedAt()
        );
    }
}
