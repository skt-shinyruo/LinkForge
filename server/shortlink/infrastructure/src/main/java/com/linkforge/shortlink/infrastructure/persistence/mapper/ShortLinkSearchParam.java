package com.linkforge.shortlink.infrastructure.persistence.mapper;

import java.time.LocalDateTime;

public class ShortLinkSearchParam {

    private final long tenantId;
    private final boolean archived;
    private final Boolean enabled;
    private final String keyword;
    private final String tag;
    private final Long applicationId;
    private final Long createdBy;
    private final String createdByType;
    private final boolean unscopedOnly;
    private final long offset;
    private final int limit;
    private final LocalDateTime cursorCreatedAtUtc;
    private final Long cursorId;

    public ShortLinkSearchParam(
            long tenantId,
            boolean archived,
            Boolean enabled,
            String keyword,
            String tag,
            Long applicationId,
            Long createdBy,
            String createdByType,
            boolean unscopedOnly,
            long offset,
            int limit,
            LocalDateTime cursorCreatedAtUtc,
            Long cursorId
    ) {
        this.tenantId = tenantId;
        this.archived = archived;
        this.enabled = enabled;
        this.keyword = keyword;
        this.tag = tag;
        this.applicationId = applicationId;
        this.createdBy = createdBy;
        this.createdByType = createdByType;
        this.unscopedOnly = unscopedOnly;
        this.offset = offset;
        this.limit = limit;
        this.cursorCreatedAtUtc = cursorCreatedAtUtc;
        this.cursorId = cursorId;
    }

    public long getTenantId() {
        return tenantId;
    }

    public boolean isArchived() {
        return archived;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public String getKeyword() {
        return keyword;
    }

    public String getTag() {
        return tag;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public String getCreatedByType() {
        return createdByType;
    }

    public boolean isUnscopedOnly() {
        return unscopedOnly;
    }

    public long getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    public LocalDateTime getCursorCreatedAtUtc() {
        return cursorCreatedAtUtc;
    }

    public Long getCursorId() {
        return cursorId;
    }
}
