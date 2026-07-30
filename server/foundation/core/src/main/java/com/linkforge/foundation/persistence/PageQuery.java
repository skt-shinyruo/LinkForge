package com.linkforge.foundation.persistence;

/**
 * 分页请求的归一化值对象。
 *
 * <p>页码是从 0 开始的偏移页；构造时把负页码归一到 0，把小于 1 的 page size 归一到 1。它不计算 SQL
 * offset，也不验证调用方是否已经越过总页数。</p>
 */
public record PageQuery(int page, int size) {

    /** 对输入页码和大小执行最小边界归一化。 */
    public PageQuery {
        page = Math.max(page, 0);
        size = Math.max(size, 1);
    }

    /**
     * 在构造归一化前把请求 size 限制到 {@code maxSize}。
     *
     * <p>非正 {@code maxSize} 自身归一为 1，因此该工厂不会返回 size 小于 1 的结果。</p>
     */
    public static PageQuery of(int page, int size, int maxSize) {
        return new PageQuery(page, Math.min(size, Math.max(maxSize, 1)));
    }
}
