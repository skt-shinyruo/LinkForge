package com.linkforge.shortlink.infrastructure.persistence.repo;

import com.linkforge.shortlink.infrastructure.persistence.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<TagEntity, Long> {

    Optional<TagEntity> findByTenantIdAndName(Long tenantId, String name);

    List<TagEntity> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
