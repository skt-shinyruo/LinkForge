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
        int maxPageSize,
        String cursor,
        boolean includeTotal
) {

    public BrowseLinksRequest(
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
        this(
                archived,
                enabled,
                keyword,
                tag,
                requestedApplicationId,
                pathApplicationId,
                page,
                size,
                maxPageSize,
                null,
                true
        );
    }
}
