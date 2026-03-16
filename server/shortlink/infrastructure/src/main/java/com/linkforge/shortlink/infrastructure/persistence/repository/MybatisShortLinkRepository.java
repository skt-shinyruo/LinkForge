package com.linkforge.shortlink.infrastructure.persistence.repository;

import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkCommandMapper;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkEntityMapper;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkQueryMapper;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkSearchParam;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MybatisShortLinkRepository implements ShortLinkRepository {

    private final ShortLinkCommandMapper commandMapper;
    private final ShortLinkQueryMapper queryMapper;

    public MybatisShortLinkRepository(
            ShortLinkCommandMapper commandMapper,
            ShortLinkQueryMapper queryMapper
    ) {
        this.commandMapper = commandMapper;
        this.queryMapper = queryMapper;
    }

    @Override
    public Optional<ShortLink> findByTenantIdAndId(long tenantId, long linkId) {
        if (tenantId <= 0 || linkId <= 0) {
            return Optional.empty();
        }
        ShortLinkEntity e = queryMapper.findByTenantIdAndId(tenantId, linkId);
        return Optional.ofNullable(ShortLinkEntityMapper.toDomain(e));
    }

    @Override
    public Optional<ShortLink> findByCode(String code) {
        String c = normalizeNullable(code);
        if (c == null) {
            return Optional.empty();
        }
        ShortLinkEntity e = queryMapper.findByCode(c);
        return Optional.ofNullable(ShortLinkEntityMapper.toDomain(e));
    }

    @Override
    public void insert(ShortLink link) {
        commandMapper.insert(ShortLinkEntityMapper.toEntity(link));
    }

    @Override
    public void update(ShortLink link) {
        commandMapper.update(ShortLinkEntityMapper.toEntity(link));
    }

    @Override
    public int deleteByTenantIdAndId(long tenantId, long linkId) {
        return commandMapper.deleteByTenantIdAndId(tenantId, linkId);
    }

    @Override
    public long countSearch(long tenantId, ShortLinkSearchQuery query) {
        ShortLinkSearchParam param = toSearchParam(tenantId, query, 0, 1);
        return queryMapper.countSearch(param);
    }

    @Override
    public List<ShortLink> listSearch(long tenantId, ShortLinkSearchQuery query, long offset, int limit) {
        ShortLinkSearchParam param = toSearchParam(tenantId, query, offset, limit);
        List<ShortLinkEntity> rows = queryMapper.listSearch(param);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(ShortLinkEntityMapper::toDomain).toList();
    }

    private static ShortLinkSearchParam toSearchParam(long tenantId, ShortLinkSearchQuery query, long offset, int limit) {
        ShortLinkSearchQuery q = query == null ? new ShortLinkSearchQuery(false, null, null, null) : query;
        return new ShortLinkSearchParam(
                tenantId,
                q.archived(),
                q.enabled(),
                normalizeNullable(q.keyword()),
                normalizeNullable(q.tag()),
                offset,
                limit
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

