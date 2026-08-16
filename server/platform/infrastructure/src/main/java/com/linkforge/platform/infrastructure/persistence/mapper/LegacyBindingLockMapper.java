package com.linkforge.platform.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LegacyBindingLockMapper {

    int lockTenant(@Param("tenantId") long tenantId);
}
