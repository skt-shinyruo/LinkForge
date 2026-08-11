package com.linkforge.foundation.persistence;

import java.util.List;

/**
 * 分页查询结果的不可变列表快照。
 *
 * <p>构造时通过 {@link List#copyOf(java.util.Collection)} 隔离调用方列表，随后不能修改结果项集合。items
 * 不可为 {@code null} 且不能含 {@code null} 元素；{@code total == -1} 表示调用方选择跳过总数查询。
 * {@code nextCursor} 仅在稳定排序查询仍有下一页时返回。</p>
 */
public record PageResult<T>(
        List<T> items,
        long total,
        int page,
        int size,
        boolean hasMore,
        String nextCursor
) {

    /** 兼容需要精确 total 的 offset 分页调用面。 */
    public PageResult(List<T> items, long total, int page, int size) {
        this(items, total, page, size, hasMoreFromTotal(total, page, size), null);
    }

    /** 创建 items 的防御性不可变副本。 */
    public PageResult {
        items = List.copyOf(items);
    }

    private static boolean hasMoreFromTotal(long total, int page, int size) {
        return total >= 0L && ((long) Math.max(page, 0) + 1L) * Math.max(size, 0) < total;
    }
}
