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

/**
 * Governance 上下文的审批、执行与审计编排服务。
 *
 * <p>提交审批时，请求和 {@code SUBMIT_REQUEST} 审计在同一事务内追加。批准时先完成主体、领域状态、
 * 权限矩阵和执行器唯一性校验，再通过持久化条件更新抢占 {@code PENDING_APPROVAL}；只有抢占成功的调用
 * 才能触发执行器。CAS 保证最终成功提交的本地事务只有一个认领者，但事务回滚后的重试仍可能重复产生
 * 事务外副作用，因此不提供跨资源的 exactly-once 保证。</p>
 *
 * <p>执行器、{@code APPROVED -> EXECUTED} 状态推进和 {@code APPROVE_REQUEST} 审计均在批准方法的
 * Spring 事务中同步编排。参与该事务的本地写入会在异常时一起回滚；事务外副作用无法由本服务撤销，
 * {@link ApprovalExecutionPort} 实现必须使用业务幂等键或乐观锁保护重试。</p>
 *
 * <p>审批权限矩阵：主体始终只能处理所属租户；平台管理员在该租户内可审批所有操作，租户管理员可审批
 * 一般操作和不超过 100000 的月短链配额，但外部域名绑定以及更高配额只允许平台管理员审批。</p>
 */
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

    /**
     * 创建待审批请求并追加提交审计。
     *
     * <p>申请人必须有效且属于 {@code tenantId}。请求 ID 和审计 ID 由服务端分配；{@code requestedAt}
     * 为空时使用注入时钟。请求插入或审计写入任一失败都会使事务回滚。提交审计固定记录
     * {@code beforeSnapshot=null} 和请求的 {@code afterSnapshot}，不会复制请求自身的 before 快照。</p>
     *
     * @param tenantId 审批所属租户，也是后续所有读取和状态更新的隔离边界
     * @param request 包含敏感操作、不透明快照、申请人与可选请求时间的命令
     * @return 持久化后的待审批视图
     * @throws BusinessException 申请人无效或租户不匹配时抛出
     */
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

    /**
     * 批准审批请求，并在存在匹配执行器时同步执行业务操作。
     *
     * <p>执行顺序具有并发含义：</p>
     * <ol>
     *     <li>校验审批人和租户，读取租户内请求，并验证请求仍为待审批且不是自审批；</li>
     *     <li>执行角色/配额权限矩阵校验，将领域操作映射为发布契约，并确认最多只有一个匹配执行器；</li>
     *     <li>用 {@link ApprovalRepository#markApprovedIfPending(long, long, long, String, String, LocalDateTime)}
     *     原子抢占请求；抢占失败直接返回“状态已变化”，不会执行下游操作或写审计；</li>
     *     <li>有执行器时同步执行，再以条件更新推进为 {@code EXECUTED}；没有执行器时请求稳定停留在
     *     {@code APPROVED}，表示完成决策但没有自动执行步骤；</li>
     *     <li>追加批准审计并重新读取最终状态。</li>
     * </ol>
     *
     * <p>该接口对重复调用不是“重复成功”式幂等：请求被其他调用抢占或已经离开待审批状态时返回业务错误。
     * 条件更新只保证单次抢占；若执行器包含非事务性副作用，事务回滚后的重试仍可能再次调用执行器，
     * 因而执行器必须自行保证幂等。批准链路只追加一条 {@code APPROVE_REQUEST} 审计；即使执行器存在，
     * 当前也没有独立的 {@code EXECUTE} 审计或审计级 {@code executedAt} 字段。</p>
     *
     * @param tenantId 审批所属租户
     * @param requestId 审批请求 ID
     * @param reason 审批理由，可为空
     * @param actor 审批人，必须属于目标租户并满足操作对应的角色矩阵
     * @param requestedAt 决策/执行时间；为空时使用注入时钟，约定为 UTC
     * @return 重新读取的最终请求；有执行器时通常为 {@code EXECUTED}，无执行器时为 {@code APPROVED}
     * @throws BusinessException 请求不存在、状态已变化、自审批、权限不足、payload 非法、执行器冲突或已知业务执行失败时抛出
     * @throws RuntimeException 执行器或持久化基础设施报告未分类运行时故障时向上传播
     */
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

    /**
     * 在状态抢占前确定唯一执行器。零个匹配项表示该操作只记录人工决策；多个匹配项属于运行时装配错误。
     */
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

    /**
     * 查询租户内审批请求。调用人必须是该租户的有效主体，结果按创建时间和 ID 倒序排列。
     */
    public List<ApprovalRequestResult> listRequests(long tenantId, UserActor actor) {
        requireActor(tenantId, actor);
        return approvalRepository.listByTenantId(tenantId).stream().map(this::toResult).toList();
    }

    /**
     * 查询租户内追加写审计事实。调用人必须是该租户的有效主体，结果按创建时间和 ID 倒序排列。
     */
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

    /**
     * 校验审批角色矩阵。
     *
     * <p>配额审批只接受 {@code applicationQuotaIncrease}、版本 1 的结构化 payload，且
     * {@code monthlyLinkLimit} 必填；无法解析、类型/版本不匹配或字段缺失均拒绝批准。</p>
     */
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

    /**
     * 在当前业务事务中追加审计事实。异常会向上传播，从而阻止状态变更在缺少对应审计时提交。
     */
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
