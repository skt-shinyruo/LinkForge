package com.linkforge.platform.infrastructure.persistence.mapper;

import com.linkforge.platform.infrastructure.persistence.entity.ApplicationEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ApplicationMapper {

    int insert(ApplicationEntity entity);

    ApplicationEntity findByTenantIdAndId(long tenantId, long applicationId);

    ApplicationEntity findByTenantIdAndApplicationKey(long tenantId, String applicationKey);

    List<ApplicationEntity> listByTenantId(long tenantId);

    List<ApplicationEntity> listAll();
}
