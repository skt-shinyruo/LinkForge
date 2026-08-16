package com.linkforge.foundation.runtime.web;

/** 保持列表响应 data 数组兼容时使用的公开 keyset 分页响应头。 */
public final class CursorPaginationHeaders {

    public static final String HAS_MORE = "X-Has-More";
    public static final String NEXT_CURSOR = "X-Next-Cursor";

    private CursorPaginationHeaders() {
    }
}
