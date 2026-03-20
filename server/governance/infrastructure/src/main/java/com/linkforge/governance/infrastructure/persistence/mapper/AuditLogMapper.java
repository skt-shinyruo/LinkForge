package com.linkforge.governance.infrastructure.persistence.mapper;

import com.linkforge.governance.infrastructure.persistence.entity.AuditLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuditLogMapper {

    int insert(AuditLogEntity entity);

    List<AuditLogEntity> listByTenantId(@Param("tenantId") long tenantId);
}
