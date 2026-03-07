package com.linkforge.accounts.infrastructure.persistence.repo;

import com.linkforge.accounts.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findFirstByEmail(String email);

    List<UserEntity> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
