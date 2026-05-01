package com.linkforge.accounts.domain;

import java.util.Objects;

public final class Tenant {

    private final long id;
    private final TenantName name;
    private final String status;

    private Tenant(long id, TenantName name, String status) {
        if (id <= 0) {
            throw new IllegalArgumentException("tenantId must be > 0");
        }
        this.id = id;
        this.name = Objects.requireNonNull(name, "tenantName");
        this.status = DomainStrings.requireNonBlankPreserved(status, "tenantStatus");
    }

    public static Tenant rehydrate(long id, TenantName name, String status) {
        return new Tenant(id, name, status);
    }

    public long id() {
        return id;
    }

    public TenantName name() {
        return name;
    }

    public String status() {
        return status;
    }

    public boolean active() {
        return AccountsConstants.STATUS_ACTIVE.equals(status);
    }
}
