package com.linkforge.shortlink.domain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class LinkTagPolicy {

    public static final int MAX_TAGS_PER_LINK = 20;
    public static final int MAX_TAG_NAME_LENGTH = 64;

    public String normalizeName(String raw) {
        if (raw == null) {
            throw invalidTag("标签名不能为空");
        }
        String value = raw.trim();
        if (value.isBlank()) {
            throw invalidTag("标签名不能为空");
        }
        if (value.length() > MAX_TAG_NAME_LENGTH) {
            throw invalidTag("标签名过长: " + value);
        }
        return value;
    }

    public Set<String> normalizeAssignment(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            if (normalized.size() >= MAX_TAGS_PER_LINK) {
                break;
            }
            String value = normalizeNullable(tag);
            if (value == null) {
                continue;
            }
            normalized.add(normalizeName(value));
        }
        return Set.copyOf(normalized);
    }

    private static String normalizeNullable(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isBlank() ? null : value;
    }

    private static ShortLinkDomainException invalidTag(String message) {
        return new ShortLinkDomainException(ShortLinkDomainException.Reason.INVALID_TAG, message);
    }
}
