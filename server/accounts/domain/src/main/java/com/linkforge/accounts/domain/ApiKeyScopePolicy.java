package com.linkforge.accounts.domain;

public class ApiKeyScopePolicy {

    public void requireApplicationBound(ApiKey apiKey) {
        if (apiKey == null || apiKey.applicationId() == null || apiKey.applicationId() <= 0) {
            throw new IllegalStateException("apiKey must be bound to an application");
        }
    }
}
