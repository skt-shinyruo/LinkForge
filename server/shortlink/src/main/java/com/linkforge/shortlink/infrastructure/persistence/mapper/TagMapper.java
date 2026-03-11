package com.linkforge.shortlink.infrastructure.persistence.mapper;

import com.linkforge.shortlink.infrastructure.persistence.entity.TagEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TagMapper {

    int insert(TagEntity tag);

    TagEntity findByTenantIdAndName(long tenantId, String name);

    List<TagEntity> findAllByTenantIdOrderByCreatedAtDesc(long tenantId);
}

