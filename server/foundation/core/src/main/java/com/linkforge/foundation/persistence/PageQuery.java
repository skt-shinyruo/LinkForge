package com.linkforge.foundation.persistence;

public record PageQuery(int page, int size) {

    public PageQuery {
        page = Math.max(page, 0);
        size = Math.max(size, 1);
    }

    public static PageQuery of(int page, int size, int maxSize) {
        return new PageQuery(page, Math.min(size, Math.max(maxSize, 1)));
    }
}
