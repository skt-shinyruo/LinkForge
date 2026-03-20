package com.linkforge.shortlink.domain;

public enum ShortLinkLifecycleState {
    DRAFT,
    PRE_RELEASE,
    ACTIVE,
    DISABLED;

    public static ShortLinkLifecycleState parseNullable(String raw) {
        if (raw == null || raw.trim().isBlank()) {
            return ACTIVE;
        }
        return ShortLinkLifecycleState.valueOf(raw.trim().toUpperCase());
    }
}
