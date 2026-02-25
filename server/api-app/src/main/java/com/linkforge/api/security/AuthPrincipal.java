package com.linkforge.api.security;

import java.util.Set;

public class AuthPrincipal {

    private final long userId;
    private final long tenantId;
    private final String email;
    private final Set<String> roles;

    public AuthPrincipal(long userId, long tenantId, String email, Set<String> roles) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.email = email;
        this.roles = roles;
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
}

