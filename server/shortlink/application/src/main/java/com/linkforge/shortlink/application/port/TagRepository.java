package com.linkforge.shortlink.application.port;

import java.time.LocalDateTime;
import java.util.List;

public interface TagRepository {

    Tag findByTenantIdAndName(long tenantId, String name);

    int insert(Tag tag);

    List<Tag> findAllByTenantIdOrderByCreatedAtDesc(long tenantId);

    record Tag(long id, long tenantId, String name, LocalDateTime createdAt) {
    }
}

