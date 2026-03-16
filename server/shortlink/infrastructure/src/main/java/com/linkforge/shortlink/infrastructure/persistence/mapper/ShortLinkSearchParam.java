package com.linkforge.shortlink.infrastructure.persistence.mapper;

public class ShortLinkSearchParam {

    private final long tenantId;
    private final boolean archived;
    private final Boolean enabled;
    private final String keyword;
    private final String tag;
    private final long offset;
    private final int limit;

    public ShortLinkSearchParam(
            long tenantId,
            boolean archived,
            Boolean enabled,
            String keyword,
            String tag,
            long offset,
            int limit
    ) {
        this.tenantId = tenantId;
        this.archived = archived;
        this.enabled = enabled;
        this.keyword = keyword;
        this.tag = tag;
        this.offset = offset;
        this.limit = limit;
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

    public long getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }
}
