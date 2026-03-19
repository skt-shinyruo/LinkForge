package com.linkforge.shortlink.infrastructure.persistence.entity;

import java.time.LocalDateTime;

public class ShortLinkEntity {

    private Long id;

    private Long tenantId;

    private String code;

    private String originalUrl;

    private String note;

    private Boolean enabled;

    private LocalDateTime expiresAt;

    /**
     * 归档时间（非空表示已归档，可恢复）。归档后不参与默认列表展示，且 Redirect 侧应视为不可用。
     */
    private LocalDateTime archivedAt;

    /**
     * 可选：按链接配置跳转状态码（301/302）；为空则使用全局默认值。
     */
    private Integer redirectStatusCode;

    /**
     * 是否启用预览确认页（浏览器请求下生效）。
     */
    private Boolean previewEnabled;

    /**
     * 可选：链接不可用（禁用/过期）时的落地页 URL。
     */
    private String unavailableLandingUrl;

    /**
     * Query 透传策略（OFF/ALLOWLIST/ALL）；为空则使用全局默认。
     */
    private String queryForwardMode;

    /**
     * Query 透传白名单（逗号分隔，支持 utm_* 前缀通配）。
     */
    private String queryForwardAllowlist;

    private String createdByType;

    private Long createdBy;

    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Integer getRedirectStatusCode() {
        return redirectStatusCode;
    }

    public void setRedirectStatusCode(Integer redirectStatusCode) {
        this.redirectStatusCode = redirectStatusCode;
    }

    public Boolean getPreviewEnabled() {
        return previewEnabled;
    }

    public void setPreviewEnabled(Boolean previewEnabled) {
        this.previewEnabled = previewEnabled;
    }

    public String getUnavailableLandingUrl() {
        return unavailableLandingUrl;
    }

    public void setUnavailableLandingUrl(String unavailableLandingUrl) {
        this.unavailableLandingUrl = unavailableLandingUrl;
    }

    public String getQueryForwardMode() {
        return queryForwardMode;
    }

    public void setQueryForwardMode(String queryForwardMode) {
        this.queryForwardMode = queryForwardMode;
    }

    public String getQueryForwardAllowlist() {
        return queryForwardAllowlist;
    }

    public void setQueryForwardAllowlist(String queryForwardAllowlist) {
        this.queryForwardAllowlist = queryForwardAllowlist;
    }

    public String getCreatedByType() {
        return createdByType;
    }

    public void setCreatedByType(String createdByType) {
        this.createdByType = createdByType;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
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
