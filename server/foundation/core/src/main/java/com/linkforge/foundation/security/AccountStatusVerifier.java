package com.linkforge.foundation.security;

public interface AccountStatusVerifier {

    void requireActiveTenant(long tenantId);

    void requireActiveUserAndTenant(long userId, long tenantId);

    void requireActiveUserAndTenant(long userId, long tenantId, int tokenVersion);
}
