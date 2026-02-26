package com.linkforge.api.iam.repo;

import com.linkforge.api.iam.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<TenantEntity, Long> {

    Optional<TenantEntity> findByName(String name);
}

