package com.linkforge.redirect.domain;

import java.util.Locale;
import java.util.Optional;

public record RedirectLookupKey(String host, String code) {

    public RedirectLookupKey {
        code = normalizeCode(code);
        if (code == null) {
            throw new IllegalArgumentException("code must be a safe short code");
        }
        host = normalizeHost(host);
    }

    public static Optional<RedirectLookupKey> tryCreate(String host, String code) {
        String normalizedCode = normalizeCode(code);
        if (normalizedCode == null) {
            return Optional.empty();
        }
        return Optional.of(new RedirectLookupKey(host, normalizedCode));
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String value = code.trim();
        if (value.isBlank() || value.length() > 32) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean ok = (ch >= '0' && ch <= '9')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z');
            if (!ok) {
                return null;
            }
        }
        return value;
    }
}
