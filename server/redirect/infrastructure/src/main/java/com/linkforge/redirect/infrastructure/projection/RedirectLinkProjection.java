package com.linkforge.redirect.infrastructure.projection;

import java.time.LocalDateTime;

public class RedirectLinkProjection {

    private String hostname;
    private String code;
    private Long tenantId;
    private Long linkId;
    private String originalUrl;
    private Boolean enabled;
    private LocalDateTime expiresAt;
    private Integer redirectStatusCode;
    private Boolean previewEnabled;
    private String unavailableLandingUrl;
    private String queryForwardMode;
    private String queryForwardAllowlist;
    private LocalDateTime updatedAt;

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getLinkId() {
        return linkId;
    }

    public void setLinkId(Long linkId) {
        this.linkId = linkId;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
