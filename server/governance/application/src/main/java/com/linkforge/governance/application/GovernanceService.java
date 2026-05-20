package com.linkforge.governance.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.governance.ApplicationQuotaIncreaseApprovalPayload;
import com.linkforge.contract.governance.ApprovalExecutionPort;
import com.linkforge.contract.governance.ApprovalExecutionRequest;
import com.linkforge.contract.governance.ApprovalPayloadCodec;
import com.linkforge.contract.governance.ApprovalPayloadTypes;
import com.linkforge.contract.governance.SensitiveOperation;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.security.StandardRoles;
import com.linkforge.governance.application.port.ApprovalRepository;
import com.linkforge.governance.application.port.AuditLogRepository;
import com.linkforge.governance.domain.ApprovalDomainException;
import com.linkforge.governance.domain.ApprovalRequest;
import com.linkforge.governance.domain.ApprovalStatus;
import com.linkforge.governance.domain.AuditLog;
import com.linkforge.governance.domain.SensitiveOperationType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class GovernanceService {

    private static final long TENANT_ADMIN_MONTHLY_LINK_LIMIT_CEILING = 100_000L;

    private final SnowflakeIdGenerator idGenerator;
    private final ApprovalRepository approvalRepository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;
    private final List<ApprovalExecutionPort> approvalExecutionPorts;

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
    public ApprovalRequestResult submitRequest(long tenantId, SubmitApprovalRequest request) {
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
        appendAuditLog(tenantId, actor, "SUBMIT_REQUEST", "approval_request", String.valueOf(requestId), requestId, null, request.afterSnapshot(), now);
        return toResult(approvalRequest);
    }

    @Transactional
    public ApprovalRequestResult approveRequest(long tenantId, long requestId, String reason, UserActor actor, LocalDateTime requestedAt) {
        UserActor effectiveActor = requireActor(tenantId, actor);
        ApprovalRequest request = approvalRepository.findByTenantIdAndId(tenantId, requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "审批请求不存在"));
        LocalDateTime now = requestedAt == null ? LocalDateTime.now(clock) : requestedAt;
        ApprovalRequest approved = approve(request, effectiveActor, reason, now);
        enforceApprovalMatrix(effectiveActor, request);
        SensitiveOperation operation = SensitiveOperationMapper.toContractOperation(request.operationType());
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

        appendAuditLog(tenantId, effectiveActor, "APPROVE_REQUEST", "approval_request", String.valueOf(requestId), requestId, request.beforeSnapshot(), request.afterSnapshot(), now);
        return approvalRepository.findByTenantIdAndId(tenantId, requestId)
                .map(this::toResult)
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
        List<ApprovalExecutionPort> matchingPorts = approvalExecutionPorts.stream()
                .filter(port -> port.supports(operation))
                .toList();
        if (matchingPorts.size() > 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "多个审批执行器支持同一操作: " + operation);
        }
        return matchingPorts.stream().findFirst();
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

    public List<ApprovalRequestResult> listRequests(long tenantId, UserActor actor) {
        requireActor(tenantId, actor);
        return approvalRepository.listByTenantId(tenantId).stream().map(this::toResult).toList();
    }

    public List<AuditLogResult> listAuditLogs(long tenantId, UserActor actor) {
        requireActor(tenantId, actor);
        return auditLogRepository.listByTenantId(tenantId).stream()
                .map(log -> new AuditLogResult(
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
        Set<String> roles = actor.roles() == null ? Set.of() : actor.roles();
        boolean isPlatformAdmin = roles.contains(StandardRoles.PLATFORM_ADMIN);
        boolean isTenantAdmin = roles.contains(StandardRoles.TENANT_ADMIN);
        if (!isPlatformAdmin && !isTenantAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无审批权限");
        }
        if (request.operationType() == SensitiveOperationType.EXTERNAL_DOMAIN_BINDING && !isPlatformAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "外部域名绑定需平台管理员审批");
        }
        if (request.operationType() == SensitiveOperationType.APPLICATION_QUOTA_INCREASE) {
            long requestedMonthlyLinkLimit = parseRequestedMonthlyLinkLimit(request.afterSnapshot());
            if (requestedMonthlyLinkLimit > TENANT_ADMIN_MONTHLY_LINK_LIMIT_CEILING && !isPlatformAdmin) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "超出租户管理员可审批的配额上限");
            }
        }
    }

    private long parseRequestedMonthlyLinkLimit(String snapshot) {
        ApplicationQuotaIncreaseApprovalPayload payload;
        try {
            payload = ApprovalPayloadCodec.read(snapshot, ApplicationQuotaIncreaseApprovalPayload.class);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "配额审批 payload 不合法");
        }
        if (!ApprovalPayloadTypes.APPLICATION_QUOTA_INCREASE.equals(payload.type())
                || payload.version() != ApprovalPayloadTypes.VERSION_1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "配额审批 payload 版本不支持");
        }
        if (payload.monthlyLinkLimit() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "配额审批 payload 缺少 monthlyLinkLimit");
        }
        return payload.monthlyLinkLimit();
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

    private ApprovalRequestResult toResult(ApprovalRequest request) {
        return new ApprovalRequestResult(
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
}
