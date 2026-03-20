package com.linkforge.redirect.infrastructure.projection;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RedirectLinkProjectionMapper {

    RedirectLinkProjection findByHostnameAndCode(@Param("hostname") String hostname, @Param("code") String code);

    int upsert(RedirectLinkProjection row);

    int deleteByHostnameAndCode(@Param("hostname") String hostname, @Param("code") String code);
}
