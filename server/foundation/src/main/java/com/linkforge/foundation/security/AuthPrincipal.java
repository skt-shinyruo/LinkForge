package com.linkforge.foundation.security;

import java.util.Set;

public class AuthPrincipal {

    private final long userId;
    private final long tenantId;
    private final String email;
    private final Set<String> roles;
    private final int tokenVersion;
    /**
     * Non-null only when authenticated via OpenAPI API key.
     *
     * <p>Note: keep nullable for backward compatibility with existing JWT/cookie flows.</p>
     */
    private final Long apiKeyId;
    private final Long applicationId;

    public AuthPrincipal(long userId, long tenantId, String email, Set<String> roles) {
        this(userId, tenantId, email, roles, 0, null, null);
    }

    public AuthPrincipal(long userId, long tenantId, String email, Set<String> roles, int tokenVersion) {
        this(userId, tenantId, email, roles, tokenVersion, null, null);
    }

    public AuthPrincipal(long userId, long tenantId, String email, Set<String> roles, Long apiKeyId) {
        this(userId, tenantId, email, roles, 0, apiKeyId, null);
    }

    public AuthPrincipal(long userId, long tenantId, String email, Set<String> roles, Long apiKeyId, Long applicationId) {
        this(userId, tenantId, email, roles, 0, apiKeyId, applicationId);
    }

    public AuthPrincipal(long userId, long tenantId, String email, Set<String> roles, int tokenVersion, Long apiKeyId) {
        this(userId, tenantId, email, roles, tokenVersion, apiKeyId, null);
    }

    public AuthPrincipal(long userId, long tenantId, String email, Set<String> roles, int tokenVersion, Long apiKeyId, Long applicationId) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.email = email;
        this.roles = roles;
        this.tokenVersion = tokenVersion;
        this.apiKeyId = apiKeyId;
        this.applicationId = applicationId;
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

    public Long getApiKeyId() {
        return apiKeyId;
    }

    public Long getApplicationId() {
        return applicationId;
    }
}
