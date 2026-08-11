package com.linkforge.shortlink.domain;

import java.util.Set;

/** 聚合对规范化 patch 的实际变化判断。 */
public record ShortLinkChangeSet(Set<Field> fields) {

    public enum Field {
        ORIGINAL_URL,
        NOTE,
        ENABLED,
        EXPIRES_AT,
        REDIRECT_STATUS_CODE,
        PREVIEW_ENABLED,
        UNAVAILABLE_LANDING_URL,
        QUERY_FORWARD_MODE,
        QUERY_FORWARD_ALLOWLIST,
        LIFECYCLE_STATE
    }

    public ShortLinkChangeSet {
        fields = fields == null || fields.isEmpty() ? Set.of() : Set.copyOf(fields);
    }

    public boolean hasChanges() {
        return !fields.isEmpty();
    }

    public boolean changed(Field field) {
        return fields.contains(field);
    }

    public boolean hasChangesOtherThan(Field field) {
        return fields.stream().anyMatch(candidate -> candidate != field);
    }
}
