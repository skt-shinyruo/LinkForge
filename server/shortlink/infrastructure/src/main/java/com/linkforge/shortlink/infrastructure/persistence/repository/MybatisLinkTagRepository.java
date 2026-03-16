package com.linkforge.shortlink.infrastructure.persistence.repository;

import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.infrastructure.persistence.mapper.LinkTagMapper;
import com.linkforge.shortlink.infrastructure.persistence.mapper.LinkTagNameRow;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Repository
public class MybatisLinkTagRepository implements LinkTagRepository {

    private final LinkTagMapper mapper;

    public MybatisLinkTagRepository(LinkTagMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insert(long linkId, long tagId) {
        return mapper.insert(linkId, tagId);
    }

    @Override
    public int deleteAllByLinkId(long linkId) {
        return mapper.deleteAllByLinkId(linkId);
    }

    @Override
    public List<String> findTagNamesByLinkId(long linkId) {
        List<String> rows = mapper.findTagNamesByLinkId(linkId);
        return rows == null ? List.of() : rows;
    }

    @Override
    public List<LinkTagName> findTagNamesByLinkIds(Collection<Long> linkIds) {
        if (linkIds == null || linkIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Long id : linkIds) {
            if (id != null && id > 0) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        List<LinkTagNameRow> rows = mapper.findTagNamesByLinkIds(ids);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<LinkTagName> out = new ArrayList<>(rows.size());
        for (LinkTagNameRow r : rows) {
            if (r == null || r.getLinkId() == null || r.getTagName() == null) {
                continue;
            }
            out.add(new LinkTagName(r.getLinkId(), r.getTagName()));
        }
        return out;
    }
}

