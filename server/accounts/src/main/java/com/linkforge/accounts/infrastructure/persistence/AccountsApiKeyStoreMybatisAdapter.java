package com.linkforge.accounts.infrastructure.persistence;

import com.linkforge.accounts.application.port.AccountsApiKeyStore;
import com.linkforge.accounts.infrastructure.persistence.entity.ApiKeyEntity;
import com.linkforge.accounts.infrastructure.persistence.mapper.ApiKeyMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class AccountsApiKeyStoreMybatisAdapter implements AccountsApiKeyStore {

    private final ApiKeyMapper apiKeyMapper;

    public AccountsApiKeyStoreMybatisAdapter(ApiKeyMapper apiKeyMapper) {
        this.apiKeyMapper = apiKeyMapper;
    }

    @Override
    public void insert(ApiKey apiKey) {
        if (apiKey == null) {
            return;
        }
        apiKeyMapper.insert(toEntity(apiKey));
    }

    @Override
    public ApiKey findById(Long apiKeyId) {
        return toApiKey(apiKeyMapper.findById(apiKeyId));
    }

    @Override
    public List<ApiKey> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId) {
        List<ApiKeyEntity> list = apiKeyMapper.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream().map(AccountsApiKeyStoreMybatisAdapter::toApiKey).toList();
    }

    @Override
    public void update(ApiKey apiKey) {
        if (apiKey == null) {
            return;
        }
        apiKeyMapper.update(toEntity(apiKey));
    }

    @Override
    public void updateLastUsedAt(Long apiKeyId, LocalDateTime lastUsedAt) {
        apiKeyMapper.updateLastUsedAt(apiKeyId, lastUsedAt);
    }

    private static ApiKeyEntity toEntity(ApiKey apiKey) {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(apiKey.id());
        entity.setTenantId(apiKey.tenantId());
        entity.setApplicationId(apiKey.applicationId());
        entity.setName(apiKey.name());
        entity.setKeyHash(apiKey.keyHash());
        entity.setStatus(apiKey.status());
        entity.setLastUsedAt(apiKey.lastUsedAt());
        entity.setCreatedAt(apiKey.createdAt());
        return entity;
    }

    private static ApiKey toApiKey(ApiKeyEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ApiKey(
                entity.getId(),
                entity.getTenantId(),
                entity.getApplicationId(),
                entity.getName(),
                entity.getKeyHash(),
                entity.getStatus(),
                entity.getLastUsedAt(),
                entity.getCreatedAt()
        );
    }
}
