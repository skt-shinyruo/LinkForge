package com.linkforge.platform.infrastructure.persistence.mapper;

import com.linkforge.platform.infrastructure.persistence.entity.ApplicationPolicyEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApplicationPolicyMapper {

    int insert(ApplicationPolicyEntity entity);

    int upsert(ApplicationPolicyEntity entity);
}
