package com.linkforge.governance.application;

import com.linkforge.accounts.domain.Roles;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.runtime.security.TenantGuard;
import com.linkforge.foundation.security.AuthContext;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.governance.application.port.ApprovalRepository;
import com.linkforge.governance.application.port.AuditLogRepository;
import com.linkforge.governance.domain.ApprovalRequest;
import com.linkforge.governance.domain.ApprovalStatus;
import com.linkforge.governance.domain.AuditLog;
import com.linkforge.governance.domain.SensitiveOperationType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class GovernanceService {

    private static final long TENANT_ADMIN_MONTHLY_LINK_LIMIT_CEILING = 100_000L;

    private final SnowflakeIdGenerator idGenerator;
    private final TenantGuard tenantGuard;
    private final ApprovalRepository approvalRepository;
    private final AuditLogRepository auditLogRepository;

    public GovernanceService(
            SnowflakeIdGenerator idGenerator,
            TenantGuard tenantGuard,
            ApprovalRepository approvalRepository,
            AuditLogRepository auditLogRepository
    ) {
        this.idGenerator = idGenerator;
        this.tenantGuard = tenantGuard;
        this.approvalRepository = approvalRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public ApprovalRequestDto submitRequest(long tenantId, SubmitApprovalRequest request) {
        tenantGuard.requireCurrentTenant(tenantId);
        AuthPrincipal principal = AuthContext.requirePrincipal();
        LocalDateTime now = LocalDateTime.now();
        long requestId = idGenerator.nextId();
        ApprovalRequest approvalRequest = new ApprovalRequest(
                requestId,
                tenantId,
                request.operationType(),
                request.targetApplicationId(),
                principal.getUserId(),
                principal.getEmail(),
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
        appendAuditLog(tenantId, principal, "SUBMIT_REQUEST", "approval_request", String.valueOf(requestId), requestId, null, request.afterSnapshot(), now);
        return toDto(approvalRequest);
    }

    @Transactional
    public ApprovalRequestDto approveRequest(long tenantId, long requestId, String reason) {
        tenantGuard.requireCurrentTenant(tenantId);
        AuthPrincipal principal = AuthContext.requirePrincipal();
        ApprovalRequest request = approvalRepository.findByTenantIdAndId(tenantId, requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "审批请求不存在"));
        if (request.requestedByUserId() == principal.getUserId()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "申请人与审批人不能是同一人");
        }
        enforceApprovalMatrix(principal, request);
        LocalDateTime now = LocalDateTime.now();
        approvalRepository.updateDecision(
                request.id(),
                ApprovalStatus.EXECUTED.name(),
                principal.getUserId(),
                principal.getEmail(),
                reason,
                now,
                now
        );
        appendAuditLog(tenantId, principal, "APPROVE_REQUEST", "approval_request", String.valueOf(requestId), requestId, request.beforeSnapshot(), request.afterSnapshot(), now);
        return approvalRepository.findByTenantIdAndId(tenantId, requestId)
                .map(this::toDto)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "审批请求更新失败"));
    }

    public List<ApprovalRequestDto> listRequests(long tenantId) {
        tenantGuard.requireCurrentTenant(tenantId);
        return approvalRepository.listByTenantId(tenantId).stream().map(this::toDto).toList();
    }

    public List<AuditLogDto> listAuditLogs(long tenantId) {
        tenantGuard.requireCurrentTenant(tenantId);
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

    private void enforceApprovalMatrix(AuthPrincipal principal, ApprovalRequest request) {
        boolean isPlatformAdmin = principal.getRoles().contains(Roles.PLATFORM_ADMIN);
        boolean isTenantAdmin = principal.getRoles().contains(Roles.TENANT_ADMIN);
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
            AuthPrincipal principal,
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
                principal.getUserId(),
                principal.getEmail(),
                actionType,
                resourceType,
                resourceId,
                requestId,
                beforeSnapshot,
                afterSnapshot,
                createdAt
        ));
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
            String afterSnapshot
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
