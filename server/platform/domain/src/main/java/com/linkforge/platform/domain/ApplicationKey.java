package com.linkforge.platform.domain;

public record ApplicationKey(String value) {

    public ApplicationKey {
        value = trimRequired(value, "applicationKey");
    }

    public static ApplicationKey of(String raw) {
        return new ApplicationKey(raw);
    }

    private static String trimRequired(String raw, String field) {
        if (raw == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String value = raw.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
