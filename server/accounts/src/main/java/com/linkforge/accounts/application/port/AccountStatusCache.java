package com.linkforge.accounts.application.port;

import java.time.Duration;

public interface AccountStatusCache {

    record UserAuthState(long tenantId, String status, int tokenVersion) {
    }

    String readTenantStatus(long tenantId);

    UserAuthState readUserAuthState(long userId);

    void writeTenantStatus(long tenantId, String status, Duration ttl);

    void writeUserAuthState(long userId, long tenantId, String status, int tokenVersion, Duration ttl);

    void evictTenantStatus(long tenantId);

    void evictUserStatus(long userId);
}
