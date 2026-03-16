package com.linkforge.shortlink.domain;

import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.INVALID_QUERY_FORWARD_ALLOWLIST_ITEM;

/**
 * Query parameter name/prefix pattern.
 *
 * <p>Allowed forms (V1):</p>
 * <ul>
 *   <li>{@code utm_source}</li>
 *   <li>{@code utm_*} (suffix '*' allowed only at the end, and pattern cannot be {@code *})</li>
 * </ul>
 *
 * <p>Characters allowed in base: {@code [0-9A-Za-z_]}</p>
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

    public static QueryParamPattern of(String raw) {
        return new QueryParamPattern(raw);
    }
}

