package com.linkforge.governance.infrastructure.persistence.entity;

import java.time.LocalDateTime;

/**
 * {@code audit_logs} 表的 MyBatis 行模型。
 *
 * <p>before/after snapshot 保存不透明原文，不在持久化层解释或改写；内容可能是版本化 JSON、
 * 历史纯文本或 {@code null}，具体格式由审计事件类型决定。</p>
 */
public class AuditLogEntity {

    private Long id;
    private Long tenantId;
    private Long actorUserId;
    private String actorEmail;
    private String actionType;
    private String resourceType;
    private String resourceId;
    private Long requestId;
    private String beforeSnapshot;
    private String afterSnapshot;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public String getBeforeSnapshot() { return beforeSnapshot; }
    public void setBeforeSnapshot(String beforeSnapshot) { this.beforeSnapshot = beforeSnapshot; }
    public String getAfterSnapshot() { return afterSnapshot; }
    public void setAfterSnapshot(String afterSnapshot) { this.afterSnapshot = afterSnapshot; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
