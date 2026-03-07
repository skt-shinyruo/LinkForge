package com.linkforge.api.iam.repo;

import com.linkforge.api.iam.entity.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, Long> {

    List<ApiKeyEntity> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);
}

