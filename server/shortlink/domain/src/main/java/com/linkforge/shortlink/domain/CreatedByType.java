package com.linkforge.shortlink.domain;

/**
 * Who created the short link.
 *
 * <p>Stored in DB as uppercase strings (e.g. {@code USER}, {@code API_KEY}).</p>
 */
public enum CreatedByType {
    USER,
    API_KEY;

    public static CreatedByType parseOrDefault(String raw, CreatedByType defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return CreatedByType.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}

