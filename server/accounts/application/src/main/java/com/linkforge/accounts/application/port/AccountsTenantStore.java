package com.linkforge.accounts.application.port;

import java.time.LocalDateTime;

public interface AccountsTenantStore {

    void insert(TenantData tenant);

    TenantData findById(Long tenantId);

    record TenantData(Long id, String name, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
