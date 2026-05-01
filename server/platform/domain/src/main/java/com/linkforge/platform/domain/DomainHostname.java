package com.linkforge.platform.domain;

import java.util.Locale;

public record DomainHostname(String value) {

    public DomainHostname {
        if (value == null) {
            throw new IllegalArgumentException("hostname must not be blank");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            throw new IllegalArgumentException("hostname must not be blank");
        }
    }

    public static DomainHostname of(String raw) {
        return new DomainHostname(raw);
    }
}
