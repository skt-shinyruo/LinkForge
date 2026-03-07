package com.linkforge.accounts.infrastructure.persistence.repo;

import com.linkforge.accounts.infrastructure.persistence.entity.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, Long> {

    List<ApiKeyEntity> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
