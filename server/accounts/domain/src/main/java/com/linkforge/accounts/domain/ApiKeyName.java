package com.linkforge.accounts.domain;

public record ApiKeyName(String value) {

    public ApiKeyName {
        value = DomainStrings.normalize(value, "apiKeyName");
    }

    public static ApiKeyName of(String raw) {
        return new ApiKeyName(raw);
    }
}
