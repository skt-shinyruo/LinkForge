package com.linkforge.shortlink.domain;

import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.INVALID_QUERY_FORWARD_MODE;

public enum QueryForwardMode {
    OFF,
    ALLOWLIST,
    ALL;

    public static QueryForwardMode parseNullable(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isBlank()) {
            return null;
        }
        String upper = t.toUpperCase();
        return switch (upper) {
            case "OFF" -> OFF;
            case "ALLOWLIST" -> ALLOWLIST;
            case "ALL" -> ALL;
            default -> throw new ShortLinkDomainException(INVALID_QUERY_FORWARD_MODE, "queryForwardMode 仅支持 OFF/ALLOWLIST/ALL");
        };
    }
}

