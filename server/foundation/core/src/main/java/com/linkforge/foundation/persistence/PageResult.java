package com.linkforge.foundation.persistence;

import java.util.List;

/**
 * 分页查询结果的不可变列表快照。
 *
 * <p>构造时通过 {@link List#copyOf(java.util.Collection)} 隔离调用方列表，随后不能修改结果项集合。items
 * 不可为 {@code null} 且不能含 {@code null} 元素；total/page/size 保持查询实现传入的值，不在此处重算或归一化。</p>
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    /** 创建 items 的防御性不可变副本。 */
    public PageResult {
        items = List.copyOf(items);
    }
}
