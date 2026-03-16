package com.linkforge.shortlink.infrastructure.persistence.mapper;

import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ShortLinkQueryMapper {

    ShortLinkEntity findByTenantIdAndId(long tenantId, long id);

    ShortLinkEntity findByCode(String code);

    ShortLinkEntity findActiveByCode(String code);

    long countSearch(ShortLinkSearchParam param);

    List<ShortLinkEntity> listSearch(ShortLinkSearchParam param);
}
