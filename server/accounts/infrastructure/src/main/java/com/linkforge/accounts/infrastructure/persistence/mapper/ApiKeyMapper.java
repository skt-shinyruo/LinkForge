package com.linkforge.accounts.infrastructure.persistence.mapper;

import com.linkforge.accounts.infrastructure.persistence.entity.ApiKeyEntity;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ApiKeyMapper {

    int insert(ApiKeyEntity apiKey);

    ApiKeyEntity findById(Long id);

    List<ApiKeyEntity> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    int update(ApiKeyEntity apiKey);

    int updateKeyHashIfCurrent(Long id, String expectedKeyHash, String newKeyHash);

    int updateLastUsedAt(Long id, LocalDateTime lastUsedAt);
}
