package com.linkforge.governance.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.security.StandardRoles;
import com.linkforge.governance.application.port.ApprovalExecutionPort;
import com.linkforge.governance.application.port.ApprovalRepository;
import com.linkforge.governance.application.port.AuditLogRepository;
import com.linkforge.governance.domain.ApprovalRequest;
import com.linkforge.governance.domain.ApprovalStatus;
import com.linkforge.governance.domain.AuditLog;
import com.linkforge.governance.domain.SensitiveOperationType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
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
        appendAuditLog(tenantId, actor, "SUBMIT_REQUEST", "approval_request", String.valueOf(requestId), requestId, null, request.afterSnapshot(), now);
        return toDto(approvalRequest);
    }

    @Transactional
    public ApprovalRequestDto approveRequest(long tenantId, long requestId, String reason, UserActor actor, LocalDateTime requestedAt) {
        UserActor effectiveActor = requireActor(tenantId, actor);
        ApprovalRequest request = approvalRepository.findByTenantIdAndId(tenantId, requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "审批请求不存在"));
        if (request.requestedByUserId() == effectiveActor.userId()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "申请人与审批人不能是同一人");
        }
        enforceApprovalMatrix(effectiveActor, request);
        LocalDateTime now = requestedAt == null ? LocalDateTime.now(clock) : requestedAt;
        executeApprovedRequest(request, now);
        approvalRepository.updateDecision(
                request.id(),
                ApprovalStatus.EXECUTED.name(),
                effectiveActor.userId(),
                effectiveActor.email(),
                reason,
                now,
                now
        );
        appendAuditLog(tenantId, effectiveActor, "APPROVE_REQUEST", "approval_request", String.valueOf(requestId), requestId, request.beforeSnapshot(), request.afterSnapshot(), now);
        return approvalRepository.findByTenantIdAndId(tenantId, requestId)
                .map(this::toDto)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "审批请求更新失败"));
    }

    private void executeApprovedRequest(ApprovalRequest request, LocalDateTime executedAt) {
        approvalExecutionPorts.stream()
                .filter(port -> port.supports(request.operationType()))
                .findFirst()
                .ifPresent(port -> port.execute(request, executedAt));
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
        if (snapshot == null || snapshot.isBlank()) {
            return 0L;
        }
        String marker = "monthlyLinkLimit=";
        int start = snapshot.indexOf(marker);
        if (start < 0) {
            return 0L;
        }
        int valueStart = start + marker.length();
        int valueEnd = snapshot.indexOf(',', valueStart);
        String raw = valueEnd < 0 ? snapshot.substring(valueStart) : snapshot.substring(valueStart, valueEnd);
        return Long.parseLong(raw.trim());
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
