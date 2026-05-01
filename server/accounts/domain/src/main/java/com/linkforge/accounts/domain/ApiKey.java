package com.linkforge.accounts.domain;

import java.util.Objects;

public final class ApiKey {

    private final long id;
    private final long tenantId;
    private final Long applicationId;
    private final ApiKeyName name;
    private final String status;

    private ApiKey(long id, long tenantId, Long applicationId, ApiKeyName name, String status) {
        if (id <= 0) {
            throw new IllegalArgumentException("apiKeyId must be > 0");
        }
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be > 0");
        }
        if (applicationId != null && applicationId <= 0) {
            throw new IllegalArgumentException("applicationId must be > 0");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.applicationId = applicationId;
        this.name = Objects.requireNonNull(name, "apiKeyName");
        this.status = DomainStrings.requireNonBlankPreserved(status, "apiKeyStatus");
    }

    public static ApiKey rehydrate(long id, long tenantId, Long applicationId, ApiKeyName name, String status) {
        return new ApiKey(id, tenantId, applicationId, name, status);
    }

    public long id() {
        return id;
    }

    public long tenantId() {
        return tenantId;
    }

    public Long applicationId() {
        return applicationId;
    }

    public ApiKeyName name() {
        return name;
    }

    public String status() {
        return status;
    }

    public boolean active() {
        return AccountsConstants.STATUS_ACTIVE.equals(status);
    }

    public ApiKey revoke() {
        return new ApiKey(id, tenantId, applicationId, name, AccountsConstants.STATUS_DISABLED);
    }

    public ApiKey activate() {
        return new ApiKey(id, tenantId, applicationId, name, AccountsConstants.STATUS_ACTIVE);
    }
}
