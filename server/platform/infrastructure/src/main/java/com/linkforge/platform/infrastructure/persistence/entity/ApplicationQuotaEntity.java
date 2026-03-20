package com.linkforge.platform.infrastructure.persistence.entity;

import java.time.LocalDateTime;

public class ApplicationQuotaEntity {

    private Long applicationId;
    private Long monthlyLinkLimit;
    private Long monthlyClickLimit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getMonthlyLinkLimit() {
        return monthlyLinkLimit;
    }

    public void setMonthlyLinkLimit(Long monthlyLinkLimit) {
        this.monthlyLinkLimit = monthlyLinkLimit;
    }

    public Long getMonthlyClickLimit() {
        return monthlyClickLimit;
    }

    public void setMonthlyClickLimit(Long monthlyClickLimit) {
        this.monthlyClickLimit = monthlyClickLimit;
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
