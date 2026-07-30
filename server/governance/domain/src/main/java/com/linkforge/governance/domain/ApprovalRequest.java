package com.linkforge.governance.domain;

import java.time.LocalDateTime;

/**
 * 敏感操作审批请求的不可变领域快照。
 *
 * <p>当前领域行为只允许 {@link ApprovalStatus#PENDING_APPROVAL} 到
 * {@link ApprovalStatus#APPROVED}，以及 {@code APPROVED} 到
 * {@link ApprovalStatus#EXECUTED} 两条转换。批准时强制申请人与审批人分离；执行状态只能在批准后写入。
 * 每次转换均返回新实例，原实例不会被修改。</p>
 *
 * <p>{@code beforeSnapshot}/{@code afterSnapshot} 是由操作类型解释的不透明文本，可能是版本化 JSON、
 * 历史纯文本或 {@code null}；领域对象只保存原文，不解释结构。公开构造器同时承担持久化重建职责，
 * 因此不会替调用方修复历史状态；新业务状态必须通过领域方法推进。</p>
 */
public class ApprovalRequest {

    private final long id;
    private final long tenantId;
    private final SensitiveOperationType operationType;
    private final Long targetApplicationId;
    private final long requestedByUserId;
    private final String requestedByEmail;
    private final ApprovalStatus status;
    private final Long approverUserId;
    private final String approverEmail;
    private final String decisionReason;
    private final String beforeSnapshot;
    private final String afterSnapshot;
    private final LocalDateTime createdAt;
    private final LocalDateTime decidedAt;
    private final LocalDateTime executedAt;

    /**
     * 从持久化字段重建审批快照。
     *
     * <p>调用方必须保证状态与审批人、决策时间、执行时间之间的一致性；该构造器不执行状态迁移校验。</p>
     */
    public ApprovalRequest(
            long id,
            long tenantId,
            SensitiveOperationType operationType,
            Long targetApplicationId,
            long requestedByUserId,
            String requestedByEmail,
            ApprovalStatus status,
            Long approverUserId,
            String approverEmail,
            String decisionReason,
            String beforeSnapshot,
            String afterSnapshot,
            LocalDateTime createdAt,
            LocalDateTime decidedAt,
            LocalDateTime executedAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.operationType = operationType;
        this.targetApplicationId = targetApplicationId;
        this.requestedByUserId = requestedByUserId;
        this.requestedByEmail = requestedByEmail;
        this.status = status;
        this.approverUserId = approverUserId;
        this.approverEmail = approverEmail;
        this.decisionReason = decisionReason;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
        this.createdAt = createdAt;
        this.decidedAt = decidedAt;
        this.executedAt = executedAt;
    }

    /**
     * 记录批准决定。
     *
     * @param approverUserId 审批人用户 ID，必须与申请人不同
     * @param approverEmail 审批时记录的审批人邮箱快照
     * @param decisionReason 审批理由，可为空
     * @param decidedAt 决策时间；应用层约定使用 UTC
     * @return 状态为 {@code APPROVED} 且保留原始 before/after 快照的新实例
     * @throws ApprovalDomainException 当前状态不是 {@code PENDING_APPROVAL}，或发生自审批时抛出
     */
    public ApprovalRequest approve(
            long approverUserId,
            String approverEmail,
            String decisionReason,
            LocalDateTime decidedAt
    ) {
        if (status != ApprovalStatus.PENDING_APPROVAL) {
            throw new ApprovalDomainException(ApprovalDomainException.Reason.APPROVAL_NOT_PENDING);
        }
        if (requestedByUserId == approverUserId) {
            throw new ApprovalDomainException(ApprovalDomainException.Reason.SELF_APPROVAL);
        }
        return new ApprovalRequest(
                id,
                tenantId,
                operationType,
                targetApplicationId,
                requestedByUserId,
                requestedByEmail,
                ApprovalStatus.APPROVED,
                approverUserId,
                approverEmail,
                decisionReason,
                beforeSnapshot,
                afterSnapshot,
                createdAt,
                decidedAt,
                null
        );
    }

    /**
     * 在敏感操作成功执行后，将批准请求推进为已执行。
     *
     * @param executedAt 执行完成时间；应用层约定使用 UTC
     * @return 状态为 {@code EXECUTED}、保留批准信息的新实例
     * @throws ApprovalDomainException 当前状态不是 {@code APPROVED} 时抛出
     */
    public ApprovalRequest markExecuted(LocalDateTime executedAt) {
        if (status != ApprovalStatus.APPROVED) {
            throw new ApprovalDomainException(ApprovalDomainException.Reason.APPROVAL_NOT_APPROVED);
        }
        return new ApprovalRequest(
                id,
                tenantId,
                operationType,
                targetApplicationId,
                requestedByUserId,
                requestedByEmail,
                ApprovalStatus.EXECUTED,
                approverUserId,
                approverEmail,
                decisionReason,
                beforeSnapshot,
                afterSnapshot,
                createdAt,
                decidedAt,
                executedAt
        );
    }

    public long id() {
        return id;
    }

    public long tenantId() {
        return tenantId;
    }

    public SensitiveOperationType operationType() {
        return operationType;
    }

    public Long targetApplicationId() {
        return targetApplicationId;
    }

    public long requestedByUserId() {
        return requestedByUserId;
    }

    public String requestedByEmail() {
        return requestedByEmail;
    }

    public ApprovalStatus status() {
        return status;
    }

    public Long approverUserId() {
        return approverUserId;
    }

    public String approverEmail() {
        return approverEmail;
    }

    public String decisionReason() {
        return decisionReason;
    }

    public String beforeSnapshot() {
        return beforeSnapshot;
    }

    public String afterSnapshot() {
        return afterSnapshot;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime decidedAt() {
        return decidedAt;
    }

    public LocalDateTime executedAt() {
        return executedAt;
    }
}
