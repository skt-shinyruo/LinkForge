package com.linkforge.accounts.domain;

import java.util.Objects;

public final class AccountUser {

    private final long id;
    private final long tenantId;
    private final EmailAddress email;
    private final String status;
    private final TokenVersion tokenVersion;

    private AccountUser(long id, long tenantId, EmailAddress email, String status, TokenVersion tokenVersion) {
        if (id <= 0) {
            throw new IllegalArgumentException("userId must be > 0");
        }
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be > 0");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.email = Objects.requireNonNull(email, "email");
        this.status = DomainStrings.requireNonBlankPreserved(status, "userStatus");
        this.tokenVersion = Objects.requireNonNull(tokenVersion, "tokenVersion");
    }

    public static AccountUser rehydrate(
            long id,
            long tenantId,
            EmailAddress email,
            String status,
            TokenVersion tokenVersion
    ) {
        return new AccountUser(id, tenantId, email, status, tokenVersion);
    }

    public long id() {
        return id;
    }

    public long tenantId() {
        return tenantId;
    }

    public EmailAddress email() {
        return email;
    }

    public String status() {
        return status;
    }

    public TokenVersion tokenVersion() {
        return tokenVersion;
    }

    public boolean active() {
        return AccountsConstants.STATUS_ACTIVE.equals(status);
    }

    public boolean disabled() {
        return !active();
    }

    public AccountUser enable() {
        return new AccountUser(id, tenantId, email, AccountsConstants.STATUS_ACTIVE, tokenVersion);
    }

    public AccountUser disable() {
        return new AccountUser(id, tenantId, email, AccountsConstants.STATUS_DISABLED, tokenVersion);
    }

    public AccountUser logout() {
        return new AccountUser(id, tenantId, email, status, tokenVersion.incremented());
    }
}
