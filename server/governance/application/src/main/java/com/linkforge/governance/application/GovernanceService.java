package com.linkforge.governance.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.governance.ApprovalExecutionPort;
import com.linkforge.contract.governance.ApprovalExecutionRequest;
import com.linkforge.contract.governance.SensitiveOperation;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.governance.domain.ApprovalActor;
import com.linkforge.governance.application.port.ApprovalRepository;
import com.linkforge.governance.application.port.AuditLogRepository;
import com.linkforge.governance.domain.ApprovalDomainException;
import com.linkforge.governance.domain.ApprovalMatrixPolicy;
import com.linkforge.governance.domain.ApprovalRequest;
import com.linkforge.governance.domain.ApprovalStatus;
import com.linkforge.governance.domain.AuditPolicy;
import com.linkforge.governance.domain.AuditLog;
import com.linkforge.governance.domain.SensitiveOperationType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class GovernanceService {

    private final SnowflakeIdGenerator idGenerator;
    private final ApprovalRepository approvalRepository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;
    private final List<ApprovalExecutionPort> approvalExecutionPorts;
    private final ApprovalMatrixPolicy approvalMatrixPolicy = new ApprovalMatrixPolicy();
    private final AuditPolicy auditPolicy = new AuditPolicy();

    public GovernanceService(
            SnowflakeIdGenerator idGenerator,
            ApprovalRepository approvalRepository,
            AuditLogRepository auditLogRepository,
            Clock clock,
            List<ApprovalExecutionPort> approvalExecutionPorts
    ) {
        this.idGenerator = idGenerator;
        this.approvalRepository = approvalRepository;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
        this.approvalExecutionPorts = approvalExecutionPorts == null ? List.of() : List.copyOf(approvalExecutionPorts);
    }

    @Transactional
    public ApprovalRequestDto submitRequest(long tenantId, SubmitApprovalRequest request) {
        UserActor actor = requireActor(tenantId, request.actor());
        LocalDateTime now = request.requestedAt() == null ? LocalDateTime.now(clock) : request.requestedAt();
        long requestId = idGenerator.nextId();
        ApprovalRequest approvalRequest = new ApprovalRequest(
                requestId,
                tenantId,
                request.operationType(),
                request.targetApplicationId(),
                actor.userId(),
                actor.email(),
                ApprovalStatus.PENDING_APPROVAL,
                null,
                null,
                null,
                request.beforeSnapshot(),
                request.afterSnapshot(),
                now,
                null,
                null
        );
        approvalRepository.insert(approvalRequest);
        appendAuditLog(
                tenantId,
                actor,
                auditPolicy.requiredActionType(AuditPolicy.AuditAction.SUBMIT_REQUEST),
                auditPolicy.requiredResourceType(),
                String.valueOf(requestId),
                requestId,
                null,
                request.afterSnapshot(),
                now
        );
        return toDto(approvalRequest);
    }

    @Transactional
    public ApprovalRequestDto approveRequest(long tenantId, long requestId, String reason, UserActor actor, LocalDateTime requestedAt) {
        UserActor effectiveActor = requireActor(tenantId, actor);
        ApprovalRequest request = approvalRepository.findByTenantIdAndId(tenantId, requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "审批请求不存在"));
        LocalDateTime now = requestedAt == null ? LocalDateTime.now(clock) : requestedAt;
        ApprovalRequest approved = approve(request, effectiveActor, reason, now);
        enforceApprovalMatrix(effectiveActor, request);
        SensitiveOperation operation = toContractOperation(request.operationType());
        Optional<ApprovalExecutionPort> executor = findExecutor(operation);

        boolean claimed = approvalRepository.markApprovedIfPending(
                approved.tenantId(),
                approved.id(),
                approved.approverUserId(),
                approved.approverEmail(),
                approved.decisionReason(),
                approved.decidedAt()
        );
        if (!claimed) {
            throw approvalStateChanged();
        }

        if (executor.isPresent()) {
            executeApprovedRequest(approved, operation, executor.get(), now);
            ApprovalRequest executed = markExecuted(approved, now);
            if (!approvalRepository.markExecutedIfApproved(executed.tenantId(), executed.id(), executed.executedAt())) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "审批执行状态更新失败");
            }
        }

        appendAuditLog(
                tenantId,
                effectiveActor,
                auditPolicy.requiredActionType(AuditPolicy.AuditAction.APPROVE_REQUEST),
                auditPolicy.requiredResourceType(),
                String.valueOf(requestId),
                requestId,
                request.beforeSnapshot(),
                request.afterSnapshot(),
                now
        );
        return approvalRepository.findByTenantIdAndId(tenantId, requestId)
                .map(this::toDto)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "审批请求更新失败"));
    }

    private static ApprovalRequest approve(
            ApprovalRequest request,
            UserActor effectiveActor,
            String reason,
            LocalDateTime decidedAt
    ) {
        try {
            return request.approve(effectiveActor.userId(), effectiveActor.email(), reason, decidedAt);
        } catch (ApprovalDomainException e) {
            throw toBusinessException(e);
        }
    }

    private static ApprovalRequest markExecuted(ApprovalRequest request, LocalDateTime executedAt) {
        try {
            return request.markExecuted(executedAt);
        } catch (ApprovalDomainException e) {
            throw toBusinessException(e);
        }
    }

    private void executeApprovedRequest(
            ApprovalRequest request,
            SensitiveOperation operation,
            ApprovalExecutionPort executor,
            LocalDateTime executedAt
    ) {
        ApprovalExecutionRequest executionRequest = new ApprovalExecutionRequest(
                request.id(),
                request.tenantId(),
                operation,
                request.targetApplicationId(),
                request.beforeSnapshot(),
                request.afterSnapshot()
        );
        executor.execute(executionRequest, executedAt);
    }

    private Optional<ApprovalExecutionPort> findExecutor(SensitiveOperation operation) {
        return approvalExecutionPorts.stream()
                .filter(port -> port.supports(operation))
                .findFirst();
    }

    private static BusinessException approvalStateChanged() {
        return new BusinessException(ErrorCode.BAD_REQUEST, "审批请求状态已变化，请刷新后重试");
    }

    private static BusinessException toBusinessException(ApprovalDomainException e) {
        if (e != null && e.reason() == ApprovalDomainException.Reason.SELF_APPROVAL) {
            return new BusinessException(ErrorCode.BAD_REQUEST, "申请人与审批人不能是同一人");
        }
        return approvalStateChanged();
    }

    private static SensitiveOperation toContractOperation(SensitiveOperationType operationType) {
        return SensitiveOperation.valueOf(operationType.name());
    }

    public List<ApprovalRequestDto> listRequests(long tenantId, UserActor actor) {
        requireActor(tenantId, actor);
        return approvalRepository.listByTenantId(tenantId).stream().map(this::toDto).toList();
    }

    public List<AuditLogDto> listAuditLogs(long tenantId, UserActor actor) {
        requireActor(tenantId, actor);
        return auditLogRepository.listByTenantId(tenantId).stream()
                .map(log -> new AuditLogDto(
                        log.id(),
                        log.tenantId(),
                        log.actorUserId(),
                        log.actorEmail(),
                        log.actionType(),
                        log.resourceType(),
                        log.resourceId(),
                        log.requestId(),
                        log.beforeSnapshot(),
                        log.afterSnapshot(),
                        log.createdAt()
                ))
                .toList();
    }

    private void enforceApprovalMatrix(UserActor actor, ApprovalRequest request) {
        ApprovalActor approvalActor = new ApprovalActor(actor.tenantId(), actor.userId(), actor.email(), actor.roles());
        boolean hasAnyApprovalRole = approvalActor.hasRole(ApprovalMatrixPolicy.PLATFORM_ADMIN)
                || approvalActor.hasRole(ApprovalMatrixPolicy.TENANT_ADMIN);
        if (!hasAnyApprovalRole) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无审批权限");
        }
        if (!approvalMatrixPolicy.mayApprove(approvalActor, request)) {
            if (request.operationType() == SensitiveOperationType.EXTERNAL_DOMAIN_BINDING) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "外部域名绑定需平台管理员审批");
            }
            if (request.operationType() == SensitiveOperationType.APPLICATION_QUOTA_INCREASE) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "超出租户管理员可审批的配额上限");
            }
            throw new BusinessException(ErrorCode.FORBIDDEN, "无审批权限");
        }
    }

    private void appendAuditLog(
            long tenantId,
            UserActor actor,
            String actionType,
            String resourceType,
            String resourceId,
            Long requestId,
            String beforeSnapshot,
            String afterSnapshot,
            LocalDateTime createdAt
    ) {
        auditLogRepository.insert(new AuditLog(
                idGenerator.nextId(),
                tenantId,
                actor.userId(),
                actor.email(),
                actionType,
                resourceType,
                resourceId,
                requestId,
                beforeSnapshot,
                afterSnapshot,
                createdAt
        ));
    }

    private static UserActor requireActor(long tenantId, UserActor actor) {
        if (actor == null || actor.userId() <= 0 || actor.email() == null || actor.email().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "actor 无效");
        }
        if (actor.tenantId() != tenantId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "actor 租户不匹配");
        }
        return actor;
    }

    private ApprovalRequestDto toDto(ApprovalRequest request) {
        return new ApprovalRequestDto(
                request.id(),
                request.tenantId(),
                request.operationType(),
                request.targetApplicationId(),
                request.requestedByUserId(),
                request.requestedByEmail(),
                request.status(),
                request.approverUserId(),
                request.approverEmail(),
                request.decisionReason()
        );
    }

    public record SubmitApprovalRequest(
            SensitiveOperationType operationType,
            Long targetApplicationId,
            String beforeSnapshot,
            String afterSnapshot,
            UserActor actor,
            LocalDateTime requestedAt
    ) {
    }

    public record ApprovalRequestDto(
            long id,
            long tenantId,
            SensitiveOperationType operationType,
            Long targetApplicationId,
            long requestedByUserId,
            String requestedByEmail,
            ApprovalStatus status,
            Long approverUserId,
            String approverEmail,
            String decisionReason
    ) {
    }

    public record AuditLogDto(
            long id,
            long tenantId,
            long actorUserId,
            String actorEmail,
            String actionType,
            String resourceType,
            String resourceId,
            Long requestId,
            String beforeSnapshot,
            String afterSnapshot,
            LocalDateTime createdAt
    ) {
    }
}
