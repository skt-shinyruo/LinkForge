package com.linkforge.accounts.infrastructure.persistence.repo;

import com.linkforge.accounts.infrastructure.persistence.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<TenantEntity, Long> {

    Optional<TenantEntity> findByName(String name);
}
