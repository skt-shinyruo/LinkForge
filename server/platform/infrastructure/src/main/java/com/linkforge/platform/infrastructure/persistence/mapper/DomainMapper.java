package com.linkforge.platform.infrastructure.persistence.mapper;

import com.linkforge.platform.infrastructure.persistence.entity.DomainEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DomainMapper {

    int insert(DomainEntity entity);

    DomainEntity findByTenantIdAndId(@Param("tenantId") long tenantId, @Param("domainId") long domainId);

    DomainEntity findByTenantIdAndHostname(@Param("tenantId") long tenantId, @Param("hostname") String hostname);

    int insertAuthorization(@Param("applicationId") long applicationId, @Param("domainId") long domainId);

    int countAuthorization(@Param("applicationId") long applicationId, @Param("domainId") long domainId);

    List<DomainEntity> listByTenantId(@Param("tenantId") long tenantId);

    List<DomainEntity> listUsableByApplication(@Param("tenantId") long tenantId, @Param("applicationId") long applicationId);

    List<DomainEntity> listAll();
}
