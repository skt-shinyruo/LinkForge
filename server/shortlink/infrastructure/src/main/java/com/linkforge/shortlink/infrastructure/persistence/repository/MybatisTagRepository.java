package com.linkforge.shortlink.infrastructure.persistence.repository;

import com.linkforge.shortlink.application.port.TagRepository;
import com.linkforge.shortlink.infrastructure.persistence.entity.TagEntity;
import com.linkforge.shortlink.infrastructure.persistence.mapper.TagMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MybatisTagRepository implements TagRepository {

    private final TagMapper mapper;

    public MybatisTagRepository(TagMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Tag findByTenantIdAndName(long tenantId, String name) {
        if (tenantId <= 0) {
            return null;
        }
        String n = normalizeNullable(name);
        if (n == null) {
            return null;
        }
        TagEntity e = mapper.findByTenantIdAndName(tenantId, n);
        return toTag(e);
    }

    @Override
    public int insert(Tag tag) {
        if (tag == null) {
            return 0;
        }
        TagEntity e = new TagEntity();
        e.setId(tag.id());
        e.setTenantId(tag.tenantId());
        e.setName(tag.name());
        return mapper.insert(e);
    }

    @Override
    public List<Tag> findAllByTenantIdOrderByCreatedAtDesc(long tenantId) {
        List<TagEntity> rows = mapper.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(MybatisTagRepository::toTag).toList();
    }

    private static Tag toTag(TagEntity e) {
        if (e == null || e.getId() == null) {
            return null;
        }
        return new Tag(
                e.getId(),
                e.getTenantId() == null ? 0L : e.getTenantId(),
                e.getName(),
                e.getCreatedAt()
        );
    }

    private static String normalizeNullable(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}

