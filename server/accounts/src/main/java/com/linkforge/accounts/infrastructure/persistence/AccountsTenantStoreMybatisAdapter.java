package com.linkforge.accounts.infrastructure.persistence;

import com.linkforge.accounts.application.port.AccountsTenantStore;
import com.linkforge.accounts.infrastructure.persistence.entity.TenantEntity;
import com.linkforge.accounts.infrastructure.persistence.mapper.TenantMapper;
import org.springframework.stereotype.Component;

@Component
public class AccountsTenantStoreMybatisAdapter implements AccountsTenantStore {

    private final TenantMapper tenantMapper;

    public AccountsTenantStoreMybatisAdapter(TenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    @Override
    public void insert(TenantData tenant) {
        if (tenant == null) {
            return;
        }
        tenantMapper.insert(toEntity(tenant));
    }

    @Override
    public TenantData findById(Long tenantId) {
        return toData(tenantMapper.findById(tenantId));
    }

    private static TenantEntity toEntity(TenantData tenant) {
        TenantEntity entity = new TenantEntity();
        entity.setId(tenant.id());
        entity.setName(tenant.name());
        entity.setStatus(tenant.status());
        entity.setCreatedAt(tenant.createdAt());
        entity.setUpdatedAt(tenant.updatedAt());
        return entity;
    }

    private static TenantData toData(TenantEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TenantData(
                entity.getId(),
                entity.getName(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
