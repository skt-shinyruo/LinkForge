package com.linkforge.shortlink.interfaces.web.dto;

import java.util.List;

public record ShortLinkPageHttpResponse<T>(
        List<T> items,
        long total,
        int page,
        int size,
        boolean hasMore,
        String nextCursor
) {

    public ShortLinkPageHttpResponse(List<T> items, long total, int page, int size) {
        this(items, total, page, size, total >= 0L && ((long) page + 1L) * size < total, null);
    }
}
