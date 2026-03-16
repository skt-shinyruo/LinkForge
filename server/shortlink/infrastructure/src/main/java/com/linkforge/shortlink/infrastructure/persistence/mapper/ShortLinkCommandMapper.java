package com.linkforge.shortlink.infrastructure.persistence.mapper;

import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShortLinkCommandMapper {

    int insert(ShortLinkEntity entity);

    int update(ShortLinkEntity entity);

    int deleteByTenantIdAndId(long tenantId, long id);
}

