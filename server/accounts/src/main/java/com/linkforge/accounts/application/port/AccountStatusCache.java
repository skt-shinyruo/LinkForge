package com.linkforge.accounts.application.port;

import java.time.Duration;

public interface AccountStatusCache {

    String readTenantStatus(long tenantId);

    String readUserStatus(long userId);

    void writeTenantStatus(long tenantId, String status, Duration ttl);

    void writeUserStatus(long userId, String status, Duration ttl);
}
