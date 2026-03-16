package com.linkforge.shortlink.application.support;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.persistence.PageQuery;

public final class OffsetPagingGuard {

    private OffsetPagingGuard() {
    }

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

