package com.linkforge.shortlink.application.query;

public record ShortLinkSearchQuery(
        boolean archived,
        Boolean enabled,
        String keyword,
        String tag,
        Long applicationId
) {
}
