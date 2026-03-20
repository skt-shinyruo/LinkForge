package com.linkforge.governance.infrastructure.persistence.entity;

import java.time.LocalDateTime;

public class ApprovalRequestEntity {

    private Long id;
    private Long tenantId;
    private String operationType;
    private Long targetApplicationId;
    private Long requestedByUserId;
    private String requestedByEmail;
    private String status;
    private Long approverUserId;
    private String approverEmail;
    private String decisionReason;
    private String beforeSnapshot;
    private String afterSnapshot;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
    private LocalDateTime executedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public Long getTargetApplicationId() { return targetApplicationId; }
    public void setTargetApplicationId(Long targetApplicationId) { this.targetApplicationId = targetApplicationId; }
    public Long getRequestedByUserId() { return requestedByUserId; }
    public void setRequestedByUserId(Long requestedByUserId) { this.requestedByUserId = requestedByUserId; }
    public String getRequestedByEmail() { return requestedByEmail; }
    public void setRequestedByEmail(String requestedByEmail) { this.requestedByEmail = requestedByEmail; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getApproverUserId() { return approverUserId; }
    public void setApproverUserId(Long approverUserId) { this.approverUserId = approverUserId; }
    public String getApproverEmail() { return approverEmail; }
    public void setApproverEmail(String approverEmail) { this.approverEmail = approverEmail; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
    public String getBeforeSnapshot() { return beforeSnapshot; }
    public void setBeforeSnapshot(String beforeSnapshot) { this.beforeSnapshot = beforeSnapshot; }
    public String getAfterSnapshot() { return afterSnapshot; }
    public void setAfterSnapshot(String afterSnapshot) { this.afterSnapshot = afterSnapshot; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
}
