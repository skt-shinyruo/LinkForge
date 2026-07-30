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

/**
 * 审批请求仓储的 MyBatis 适配器。
 *
 * <p>所有读取和状态更新都带租户条件，避免仅凭全局 ID 穿透租户边界。审批与执行状态通过
 * 单条条件更新完成，受影响行数为零表示请求不存在、租户不匹配或状态已被并发请求推进；
 * 适配器不会把这些情况重新读取后猜测为成功。</p>
 *
 * <p>该适配器不自行开启事务，审批记录、业务执行和审计记录是否原子提交由应用服务事务决定。
 * 数据库异常不会被吞掉，由 MyBatis/Spring 翻译后向上层传播。</p>
 */
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

    /**
     * 仅当记录仍为 {@code PENDING_APPROVAL} 时认领审批。
     *
     * <p>当前 CAS 条件是 {@code tenantId + requestId + status}，不包含执行器标识；唯一执行器的
     * 选择与冲突校验必须在应用层进入此 CAS 前完成。重复调用或并发输家返回 {@code false}。</p>
     */
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

    /**
     * 仅当记录仍为 {@code APPROVED} 时标记执行完成。
     *
     * <p>返回 {@code false} 不是幂等成功，而是状态竞争或目标不匹配信号，调用方必须将其视为
     * 执行状态未被可靠记录。</p>
     */
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
