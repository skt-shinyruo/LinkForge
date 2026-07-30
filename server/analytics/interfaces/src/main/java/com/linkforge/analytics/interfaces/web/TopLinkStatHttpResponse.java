package com.linkforge.analytics.interfaces.web;

/**
 * Top 短链报表行的 HTTP 响应。
 *
 * <p>展示摘要由 Shortlink 读端口补齐，可能因短链已删除或 catalog 延迟而缺失。此时
 * {@code deleted} 为 {@code true}，客户端不得将 {@code code}、{@code shortUrl} 或
 * {@code originalUrl} 当作 Redirect 的权威数据。目标 URL 可能包含业务路径和查询参数，
 * 仅可在当前租户的受控管理界面使用。
 *
 * <p>多日 {@code uv} 是日近似 UV 的累加，不能解释为精确区间 UV。
 *
 * @param linkId 短链 ID
 * @param code 短码；历史或不可补齐的摘要可为 {@code null}
 * @param shortUrl 短链接展示地址；不可补齐时可为 {@code null}
 * @param originalUrl 目标 URL；不可补齐时可为 {@code null}
 * @param pv 查询范围内的访问次数
 * @param uv 查询范围内的近似访问量
 * @param deleted 短链当前已删除或无法作为可见摘要读取时为 {@code true}
 */
public record TopLinkStatHttpResponse(
        long linkId,
        String code,
        String shortUrl,
        String originalUrl,
        long pv,
        long uv,
        boolean deleted
) {
}
