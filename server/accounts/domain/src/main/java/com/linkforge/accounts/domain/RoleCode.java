package com.linkforge.accounts.domain;

public record RoleCode(String value) {

    public RoleCode {
        value = DomainStrings.normalize(value, "roleCode");
    }

    public static RoleCode of(String raw) {
        return new RoleCode(raw);
    }
}
