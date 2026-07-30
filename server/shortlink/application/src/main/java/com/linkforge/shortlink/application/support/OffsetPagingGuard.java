package com.linkforge.shortlink.application.support;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.persistence.PageQuery;

/**
 * 将页码分页转换为 long offset，并统一限制深分页成本。
 *
 * <p>本工具只保护 offset 上限和乘法溢出，不负责限制 page size、验证租户权限或判断结果总数；
 * {@link PageQuery} 已负责把 page/size 规范化为非负页码和正数大小。</p>
 */
public final class OffsetPagingGuard {

    private OffsetPagingGuard() {
    }

    /**
     * 计算 {@code page * size}，并要求结果不超过允许的最大 offset。
     *
     * <p>乘法先提升为 {@code long}，避免大页码在 int 范围内回绕。边界值 {@code offset == maxOffset}
     * 允许通过；超过时返回带当前参数和最大页码的 {@link ErrorCode#BAD_REQUEST} 业务异常。</p>
     *
     * @param pageQuery 非空且已规范化的分页参数
     * @param maxOffset 允许的最大 offset，调用方应传入非负数
     * @return 可安全交给持久化查询的 long offset
     * @throws BusinessException 分页参数为空或 offset 超过上限时抛出
     */
    public static long requireOffsetWithin(PageQuery pageQuery, long maxOffset) {
        if (pageQuery == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分页参数不能为空");
        }

        int page = pageQuery.page();
        int size = pageQuery.size();
        long offset = (long) page * (long) size;
        if (offset > maxOffset) {
            long maxPage = maxOffset / (long) Math.max(size, 1);
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "分页参数过大（page=" + page + ", size=" + size + "），最大允许 page=" + maxPage + "（offset≤" + maxOffset + "）。"
            );
        }
        return offset;
    }
}
