package com.linkforge.platform.infrastructure.persistence.entity;

import java.time.LocalDateTime;

public class ApplicationPolicyEntity {

    private Long applicationId;
    private String defaultDomainScope;
    private Integer defaultRedirectStatusCode;
    private Boolean previewEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getDefaultDomainScope() {
        return defaultDomainScope;
    }

    public void setDefaultDomainScope(String defaultDomainScope) {
        this.defaultDomainScope = defaultDomainScope;
    }

    public Integer getDefaultRedirectStatusCode() {
        return defaultRedirectStatusCode;
    }

    public void setDefaultRedirectStatusCode(Integer defaultRedirectStatusCode) {
        this.defaultRedirectStatusCode = defaultRedirectStatusCode;
    }

    public Boolean getPreviewEnabled() {
        return previewEnabled;
    }

    public void setPreviewEnabled(Boolean previewEnabled) {
        this.previewEnabled = previewEnabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
