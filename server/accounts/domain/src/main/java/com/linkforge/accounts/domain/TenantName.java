package com.linkforge.accounts.domain;

public record TenantName(String value) {

    public TenantName {
        value = DomainStrings.normalize(value, "tenantName");
    }

    public static TenantName of(String raw) {
        return new TenantName(raw);
    }
}
