package com.linkforge.accounts.application.port;

import java.time.LocalDateTime;
import java.util.List;

public interface AccountsApiKeyStore {

    void insert(ApiKey apiKey);

    ApiKey findById(Long apiKeyId);

    List<ApiKey> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    void update(ApiKey apiKey);

    void updateLastUsedAt(Long apiKeyId, LocalDateTime lastUsedAt);

    record ApiKey(
            Long id,
            Long tenantId,
            String name,
            String keyHash,
            String status,
            LocalDateTime lastUsedAt,
            LocalDateTime createdAt
    ) {
    }
}
