package com.linkforge.foundation.security;

import java.util.Set;

public class AuthPrincipal {

    private final long userId;
    private final long tenantId;
    private final String email;
    private final Set<String> roles;
    private final int tokenVersion;

    public AuthPrincipal(long userId, long tenantId, String email, Set<String> roles) {
        this(userId, tenantId, email, roles, 0);
    }

    public AuthPrincipal(long userId, long tenantId, String email, Set<String> roles, int tokenVersion) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.email = email;
        this.roles = roles;
        this.tokenVersion = tokenVersion;
    }

    public long getUserId() {
        return userId;
    }

    public long getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

}
