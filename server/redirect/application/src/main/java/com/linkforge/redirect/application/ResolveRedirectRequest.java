package com.linkforge.redirect.application;

/**
 * 一次跳转解析请求的应用层输入。
 *
 * <p>{@code htmlRequest} 只影响预览与错误页形态，{@code confirmed} 只由保留参数
 * {@code __lf_confirm} 得出。host 为空时允许走 legacy/unscoped 兼容查询。</p>
 */
public record ResolveRedirectRequest(
        String code,
        String host,
        boolean htmlRequest,
        boolean confirmed,
        RedirectVisitInput visitInput
) {
}
