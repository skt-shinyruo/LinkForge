package com.linkforge.shortlink.domain;

import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.INVALID_QUERY_FORWARD_ALLOWLIST_ITEM;

/**
 * 查询参数名或参数名前缀模式。
 *
 * <p>支持以下形式：</p>
 * <ul>
 *   <li>{@code utm_source}：精确匹配参数名；</li>
 *   <li>{@code utm_*}：仅允许在末尾出现一个 {@code *}，表示前缀匹配。</li>
 * </ul>
 *
 * <p>构造时去除首尾空白；基础部分只能包含 ASCII {@code [0-9A-Za-z_]}，且不允许空基础或单独的
 * {@code *}。模式保留大小写，消费方按大小写敏感的精确/前缀规则匹配。</p>
 */
public record QueryParamPattern(String value) {

    public QueryParamPattern {
        if (value == null) {
            throw new ShortLinkDomainException(INVALID_QUERY_FORWARD_ALLOWLIST_ITEM, "queryForwardAllowlist 包含不合法项: null");
        }
        value = value.trim();
        if (value.isBlank()) {
            throw new ShortLinkDomainException(INVALID_QUERY_FORWARD_ALLOWLIST_ITEM, "queryForwardAllowlist 包含不合法项: (blank)");
        }
        if ("*".equals(value)) {
            throw new ShortLinkDomainException(INVALID_QUERY_FORWARD_ALLOWLIST_ITEM, "queryForwardAllowlist 包含不合法项: *");
        }

        boolean star = value.endsWith("*");
        String base = star ? value.substring(0, value.length() - 1) : value;
        if (base.isBlank()) {
            throw new ShortLinkDomainException(INVALID_QUERY_FORWARD_ALLOWLIST_ITEM, "queryForwardAllowlist 包含不合法项: " + value);
        }
        for (int i = 0; i < base.length(); i++) {
            char ch = base.charAt(i);
            boolean ok = (ch >= '0' && ch <= '9')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z')
                    || ch == '_';
            if (!ok) {
                throw new ShortLinkDomainException(INVALID_QUERY_FORWARD_ALLOWLIST_ITEM, "queryForwardAllowlist 包含不合法项: " + value);
            }
        }
    }

    /**
     * 从外部文本创建并校验一个模式。
     */
    public static QueryParamPattern of(String raw) {
        return new QueryParamPattern(raw);
    }
}
