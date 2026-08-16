package com.linkforge.platform.infrastructure.persistence.mapper;

import com.linkforge.platform.infrastructure.persistence.entity.ApplicationQuotaEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApplicationQuotaMapper {

    int insert(ApplicationQuotaEntity entity);

    int upsert(ApplicationQuotaEntity entity);

    ApplicationQuotaEntity findByApplicationId(long applicationId);
}
