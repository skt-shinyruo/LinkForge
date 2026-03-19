package com.linkforge.shortlink.domain;

import java.time.LocalDateTime;
import java.util.Objects;

import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.DELETE_REQUIRES_ARCHIVE;
import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.INVALID_LINK_ID;
import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.INVALID_REDIRECT_STATUS_CODE;
import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.INVALID_TENANT_ID;
import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.NOTE_TOO_LONG;
import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.UPDATE_NOT_ALLOWED_WHEN_ARCHIVED;

public class ShortLink {

    private final long id;
    private final long tenantId;
    private final ShortCode code;
    private long version;

    private HttpUrl originalUrl;
    private String note;
    private boolean enabled;
    /**
     * Business semantics: treat as UTC LocalDateTime (MySQL DATETIME, no timezone).
     */
    private LocalDateTime expiresAtUtc;
    /**
     * Business semantics: treat as UTC LocalDateTime.
     */
    private LocalDateTime archivedAtUtc;
    private Integer redirectStatusCode;
    private boolean previewEnabled;
    private HttpUrl unavailableLandingUrl;
    private QueryForwardMode queryForwardMode;
    private QueryForwardAllowlist queryForwardAllowlist;

    private final long createdBy;
    private final CreatedByType createdByType;
    private LocalDateTime createdAtUtc;
    private LocalDateTime updatedAtUtc;

    private ShortLink(
            long id,
            long tenantId,
            ShortCode code,
            HttpUrl originalUrl,
            String note,
            boolean enabled,
            LocalDateTime expiresAtUtc,
            LocalDateTime archivedAtUtc,
            Integer redirectStatusCode,
            boolean previewEnabled,
            HttpUrl unavailableLandingUrl,
            QueryForwardMode queryForwardMode,
            QueryForwardAllowlist queryForwardAllowlist,
            CreatedByType createdByType,
            long createdBy,
            long version,
            LocalDateTime createdAtUtc,
            LocalDateTime updatedAtUtc
    ) {
        if (id <= 0) {
            throw new ShortLinkDomainException(INVALID_LINK_ID, "linkId 必须 > 0");
        }
        if (tenantId <= 0) {
            throw new ShortLinkDomainException(INVALID_TENANT_ID, "tenantId 必须 > 0");
        }
        if (code == null) {
            throw new ShortLinkDomainException(ShortLinkDomainException.Reason.INVALID_CODE, "短码不能为空");
        }
        if (originalUrl == null) {
            throw new ShortLinkDomainException(ShortLinkDomainException.Reason.INVALID_URL, "originalUrl 不能为空");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.originalUrl = originalUrl;
        this.note = normalizeNote(note);
        this.enabled = enabled;
        this.expiresAtUtc = expiresAtUtc;
        this.archivedAtUtc = archivedAtUtc;
        this.redirectStatusCode = validateRedirectStatusCode(redirectStatusCode);
        this.previewEnabled = previewEnabled;
        this.unavailableLandingUrl = unavailableLandingUrl;
        this.queryForwardMode = queryForwardMode;
        this.queryForwardAllowlist = queryForwardAllowlist == null ? QueryForwardAllowlist.empty() : queryForwardAllowlist;
        this.createdBy = createdBy;
        this.createdByType = createdByType == null ? CreatedByType.USER : createdByType;
        this.version = Math.max(version, 0L);
        this.createdAtUtc = createdAtUtc;
        this.updatedAtUtc = updatedAtUtc;
    }

    public static ShortLink create(
            long id,
            long tenantId,
            ShortCode code,
            HttpUrl originalUrl,
            String note,
            Boolean enabled,
            LocalDateTime expiresAtUtc,
            Integer redirectStatusCode,
            Boolean previewEnabled,
            HttpUrl unavailableLandingUrl,
            QueryForwardMode queryForwardMode,
            QueryForwardAllowlist queryForwardAllowlist,
            CreatedByType createdByType,
            long createdBy
    ) {
        boolean en = enabled == null || enabled;
        boolean preview = previewEnabled != null && previewEnabled;
        return new ShortLink(
                id,
                tenantId,
                code,
                originalUrl,
                note,
                en,
                expiresAtUtc,
                null,
                redirectStatusCode,
                preview,
                unavailableLandingUrl,
                queryForwardMode,
                queryForwardAllowlist,
                createdByType,
                createdBy,
                0L,
                null,
                null
        );
    }

    public static ShortLink rehydrate(
            long id,
            long tenantId,
            ShortCode code,
            HttpUrl originalUrl,
            String note,
            boolean enabled,
            LocalDateTime expiresAtUtc,
            LocalDateTime archivedAtUtc,
            Integer redirectStatusCode,
            boolean previewEnabled,
            HttpUrl unavailableLandingUrl,
            QueryForwardMode queryForwardMode,
            QueryForwardAllowlist queryForwardAllowlist,
            CreatedByType createdByType,
            long createdBy,
            long version,
            LocalDateTime createdAtUtc,
            LocalDateTime updatedAtUtc
    ) {
        return new ShortLink(
                id,
                tenantId,
                code,
                originalUrl,
                note,
                enabled,
                expiresAtUtc,
                archivedAtUtc,
                redirectStatusCode,
                previewEnabled,
                unavailableLandingUrl,
                queryForwardMode,
                queryForwardAllowlist,
                createdByType,
                createdBy,
                version,
                createdAtUtc,
                updatedAtUtc
        );
    }

    public long id() {
        return id;
    }

    public long tenantId() {
        return tenantId;
    }

    public ShortCode code() {
        return code;
    }

    public HttpUrl originalUrl() {
        return originalUrl;
    }

    public String note() {
        return note;
    }

    public boolean enabled() {
        return enabled;
    }

    public LocalDateTime expiresAtUtc() {
        return expiresAtUtc;
    }

    public LocalDateTime archivedAtUtc() {
        return archivedAtUtc;
    }

    public Integer redirectStatusCode() {
        return redirectStatusCode;
    }

    public boolean previewEnabled() {
        return previewEnabled;
    }

    public HttpUrl unavailableLandingUrl() {
        return unavailableLandingUrl;
    }

    public QueryForwardMode queryForwardMode() {
        return queryForwardMode;
    }

    public QueryForwardAllowlist queryForwardAllowlist() {
        return queryForwardAllowlist;
    }

    public long createdBy() {
        return createdBy;
    }

    public long version() {
        return version;
    }

    public CreatedByType createdByType() {
        return createdByType;
    }

    public LocalDateTime createdAtUtc() {
        return createdAtUtc;
    }

    public LocalDateTime updatedAtUtc() {
        return updatedAtUtc;
    }

    public void archive(LocalDateTime nowUtc) {
        Objects.requireNonNull(nowUtc, "nowUtc must be provided in UTC");
        if (archivedAtUtc == null) {
            archivedAtUtc = nowUtc;
        }
    }

    public void restore() {
        archivedAtUtc = null;
    }

    public void requireNotArchivedForUpdate() {
        if (archivedAtUtc != null) {
            throw new ShortLinkDomainException(UPDATE_NOT_ALLOWED_WHEN_ARCHIVED, "短链已归档，请先恢复后再编辑");
        }
    }

    public void requireArchivedBeforeDelete() {
        if (archivedAtUtc == null) {
            throw new ShortLinkDomainException(DELETE_REQUIRES_ARCHIVE, "删除前请先归档（可避免误删）");
        }
    }

    public void changeOriginalUrl(HttpUrl newUrl) {
        if (newUrl == null) {
            throw new ShortLinkDomainException(ShortLinkDomainException.Reason.INVALID_URL, "originalUrl 不能为空");
        }
        this.originalUrl = newUrl;
    }

    public void changeNote(String note) {
        this.note = normalizeNote(note);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setExpiresAtUtc(LocalDateTime expiresAtUtc) {
        this.expiresAtUtc = expiresAtUtc;
    }

    public void clearExpiresAtUtc() {
        this.expiresAtUtc = null;
    }

    public void setRedirectStatusCode(Integer redirectStatusCode) {
        this.redirectStatusCode = validateRedirectStatusCode(redirectStatusCode);
    }

    public void clearRedirectStatusCode() {
        this.redirectStatusCode = null;
    }

    public void setPreviewEnabled(boolean previewEnabled) {
        this.previewEnabled = previewEnabled;
    }

    public void setUnavailableLandingUrl(HttpUrl url) {
        this.unavailableLandingUrl = url;
    }

    public void clearUnavailableLandingUrl() {
        this.unavailableLandingUrl = null;
    }

    public void setQueryForwardMode(QueryForwardMode mode) {
        this.queryForwardMode = mode;
    }

    public void clearQueryForwardMode() {
        this.queryForwardMode = null;
    }

    public void setQueryForwardAllowlist(QueryForwardAllowlist allowlist) {
        this.queryForwardAllowlist = allowlist == null ? QueryForwardAllowlist.empty() : allowlist;
    }

    public void setCreatedAtUtc(LocalDateTime createdAtUtc) {
        this.createdAtUtc = createdAtUtc;
    }

    public void setUpdatedAtUtc(LocalDateTime updatedAtUtc) {
        this.updatedAtUtc = updatedAtUtc;
    }

    public void incrementVersion() {
        this.version++;
    }

    private static Integer validateRedirectStatusCode(Integer status) {
        if (status == null) {
            return null;
        }
        if (status != 301 && status != 302) {
            throw new ShortLinkDomainException(INVALID_REDIRECT_STATUS_CODE, "redirectStatusCode 仅支持 301/302");
        }
        return status;
    }

    private static String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        if (note.length() > 512) {
            throw new ShortLinkDomainException(NOTE_TOO_LONG, "备注过长");
        }
        return note;
    }
}
