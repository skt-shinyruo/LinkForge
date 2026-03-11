package com.linkforge.accounts.infrastructure.persistence.mapper;

import com.linkforge.accounts.infrastructure.persistence.entity.TenantEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantMapper {

    int insert(TenantEntity tenant);

    TenantEntity findById(Long id);

    TenantEntity findByName(String name);
}
