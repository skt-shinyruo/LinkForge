package com.linkforge.accounts.infrastructure.persistence;

import com.linkforge.accounts.application.port.AccountsApiKeyStore;
import com.linkforge.accounts.infrastructure.persistence.entity.ApiKeyEntity;
import com.linkforge.accounts.infrastructure.persistence.mapper.ApiKeyMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key 存储端口的 MyBatis 适配器。
 *
 * <p>本类不自行开启事务，所有写入参与调用方应用服务的 Spring 事务。单条查询未命中返回
 * {@code null}，列表查询无结果返回不可变空列表；传入 {@code null} 的聚合写入被视为无操作。
 * Mapper 的影响行数不会向端口暴露，因此更新方法本身不能用于判断记录是否存在或实现 CAS。</p>
 *
 * <p>{@code applicationId == null} 会按原值保留，用于识别历史未绑定记录；适配器不会猜测绑定关系。
 * {@code lastUsedAt} 沿用应用层约定的 UTC {@link LocalDateTime}，数据库列不携带时区。</p>
 */
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
    public boolean updateKeyHashIfCurrent(Long apiKeyId, String expectedKeyHash, String newKeyHash) {
        return apiKeyMapper.updateKeyHashIfCurrent(apiKeyId, expectedKeyHash, newKeyHash) > 0;
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
