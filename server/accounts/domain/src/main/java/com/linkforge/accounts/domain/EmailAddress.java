package com.linkforge.accounts.domain;

import java.util.Locale;

public record EmailAddress(String value) {

    public EmailAddress {
        value = DomainStrings.normalize(value, "email").toLowerCase(Locale.ROOT);
        if (!value.contains("@")) {
            throw new IllegalArgumentException("email must contain @");
        }
    }

    public static EmailAddress of(String raw) {
        return new EmailAddress(raw);
    }
}
