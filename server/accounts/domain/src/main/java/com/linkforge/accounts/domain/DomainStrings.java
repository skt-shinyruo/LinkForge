package com.linkforge.accounts.domain;

final class DomainStrings {

    private DomainStrings() {
    }

    static String normalize(String raw, String fieldName) {
        if (raw == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String value = raw.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    static String requireNonBlankPreserved(String raw, String fieldName) {
        if (raw == null || raw.trim().isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return raw;
    }
}
