package com.linkforge.shortlink.application.query;

import com.linkforge.shortlink.domain.CreatedByType;

public record ShortLinkSearchQuery(
        boolean archived,
        Boolean enabled,
        String keyword,
        String tag,
        Long applicationId,
        Long createdBy,
        CreatedByType createdByType,
        boolean unscopedOnly
) {
    public ShortLinkSearchQuery(
            boolean archived,
            Boolean enabled,
            String keyword,
            String tag,
            Long applicationId
    ) {
        this(archived, enabled, keyword, tag, applicationId, null, null, false);
    }
}
