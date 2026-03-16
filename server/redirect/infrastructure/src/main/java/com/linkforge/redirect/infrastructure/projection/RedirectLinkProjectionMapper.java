package com.linkforge.redirect.infrastructure.projection;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RedirectLinkProjectionMapper {

    RedirectLinkProjection findByCode(String code);

    int upsert(RedirectLinkProjection row);

    int deleteByCode(String code);
}

