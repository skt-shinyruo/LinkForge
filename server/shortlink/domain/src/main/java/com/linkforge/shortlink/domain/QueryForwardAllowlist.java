package com.linkforge.shortlink.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.INVALID_QUERY_FORWARD_ALLOWLIST_TOO_LONG;

public final class QueryForwardAllowlist {

    private static final int MAX_ITEMS = 50;
    private static final int MAX_SERIALIZED_LENGTH = 1024;

    private static final QueryForwardAllowlist EMPTY = new QueryForwardAllowlist(List.of());

    private final List<QueryParamPattern> patterns;

    private QueryForwardAllowlist(List<QueryParamPattern> patterns) {
        this.patterns = patterns == null ? List.of() : List.copyOf(patterns);
        requireSerializedLengthWithinLimit();
    }

    public static QueryForwardAllowlist empty() {
        return EMPTY;
    }

    public static QueryForwardAllowlist fromRaw(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return empty();
        }

        Set<String> uniq = new LinkedHashSet<>();
        for (String s : raw) {
            String t = normalizeNullable(s);
            if (t == null) {
                continue;
            }
            uniq.add(t);
            if (uniq.size() >= MAX_ITEMS) {
                break;
            }
        }
        if (uniq.isEmpty()) {
            return empty();
        }

        List<QueryParamPattern> out = new ArrayList<>(uniq.size());
        for (String p : uniq) {
            out.add(QueryParamPattern.of(p));
        }
        return new QueryForwardAllowlist(out);
    }

    public static QueryForwardAllowlist parseSerialized(String raw) {
        String s = normalizeNullable(raw);
        if (s == null) {
            return empty();
        }
        String[] parts = s.split(",");
        List<String> list = new ArrayList<>();
        for (String p : parts) {
            String t = normalizeNullable(p);
            if (t != null) {
                list.add(t);
            }
        }
        return fromRaw(list);
    }

    public boolean isEmpty() {
        return patterns.isEmpty();
    }

    public List<String> values() {
        if (patterns.isEmpty()) {
            return List.of();
        }
        return patterns.stream().map(QueryParamPattern::value).toList();
    }

    /**
     * Serialize for persistence (comma separated), null means "no allowlist".
     */
    public String serializeOrNull() {
        if (patterns.isEmpty()) {
            return null;
        }
        String joined = String.join(",", values());
        return joined.isBlank() ? null : joined;
    }

    private void requireSerializedLengthWithinLimit() {
        String joined = serializeOrNull();
        if (joined == null) {
            return;
        }
        if (joined.length() > MAX_SERIALIZED_LENGTH) {
            throw new ShortLinkDomainException(INVALID_QUERY_FORWARD_ALLOWLIST_TOO_LONG, "queryForwardAllowlist 过长");
        }
    }

    private static String normalizeNullable(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}

