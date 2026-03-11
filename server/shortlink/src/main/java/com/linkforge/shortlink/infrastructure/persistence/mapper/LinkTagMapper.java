package com.linkforge.shortlink.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface LinkTagMapper {

    int insert(long linkId, long tagId);

    int deleteAllByLinkId(long linkId);

    List<String> findTagNamesByLinkId(long linkId);

    List<LinkTagNameRow> findTagNamesByLinkIds(List<Long> linkIds);
}

