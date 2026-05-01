package com.linkforge.platform.domain;

public enum ApplicationStatus {
    ACTIVE,
    DISABLED;

    public static ApplicationStatus fromPersistence(String raw) {
        if (raw == null || raw.isBlank()) {
            return ACTIVE;
        }
        return ApplicationStatus.valueOf(raw.trim().toUpperCase());
    }
}
