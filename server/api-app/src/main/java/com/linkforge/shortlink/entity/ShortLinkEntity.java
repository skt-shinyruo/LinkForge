package com.linkforge.shortlink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "short_links")
public class ShortLinkEntity {

    @Id
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Column(length = 512)
    private String note;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * 归档时间（非空表示已归档，可恢复）。归档后不参与默认列表展示，且 Redirect 侧应视为不可用。
     */
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    /**
     * 可选：按链接配置跳转状态码（301/302）；为空则使用全局默认值。
     */
    @Column(name = "redirect_status_code")
    private Integer redirectStatusCode;

    /**
     * 是否启用预览确认页（浏览器请求下生效）。
     */
    @Column(name = "preview_enabled", nullable = false)
    private Boolean previewEnabled;

    /**
     * 可选：链接不可用（禁用/过期）时的落地页 URL。
     */
    @Column(name = "unavailable_landing_url", columnDefinition = "TEXT")
    private String unavailableLandingUrl;

    /**
     * Query 透传策略（OFF/ALLOWLIST/ALL）；为空则使用全局默认。
     */
    @Column(name = "query_forward_mode", length = 16)
    private String queryForwardMode;

    /**
     * Query 透传白名单（逗号分隔，支持 utm_* 前缀通配）。
     */
    @Column(name = "query_forward_allowlist", length = 1024)
    private String queryForwardAllowlist;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
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

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
