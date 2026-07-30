package com.linkforge.shortlink.application.query;

import com.linkforge.shortlink.domain.CreatedByType;

/**
 * 短链列表与导出的内部过滤条件。
 *
 * <p>{@code archived} 明确选择“仅归档”或“仅未归档”，不会同时返回两者；{@code enabled}、keyword、tag、
 * applicationId、createdBy 和 createdByType 为 {@code null} 时不增加对应条件，空白字符串由仓储归一化为未设置。
 * {@code unscopedOnly=true} 额外要求 {@code application_id IS NULL}，通常与 createdBy/createdByType 组合为
 * 普通用户的个人短链范围；它与非空 applicationId 同传时条件相交，通常得到空结果。</p>
 *
 * <p>该对象不是授权令牌。用户、API Key 与路径参数的权限校验必须在构造它之前完成，查询仓储仍会独立附加
 * tenantId 条件。</p>
 */
public record ShortLinkSearchQuery(
        boolean archived,
        Boolean enabled,
        String keyword,
        String tag,
        Long applicationId,
        Long createdBy,
        CreatedByType createdByType,
        boolean unscopedOnly
) {
    public ShortLinkSearchQuery(
            boolean archived,
            Boolean enabled,
            String keyword,
            String tag,
            Long applicationId
    ) {
        this(archived, enabled, keyword, tag, applicationId, null, null, false);
    }
}
