package com.linkforge.shortlink.application;

public record BrowseLinksRequest(
        Boolean archived,
        Boolean enabled,
        String keyword,
        String tag,
        Long requestedApplicationId,
        Long pathApplicationId,
        int page,
        int size,
        int maxPageSize
) {
}
