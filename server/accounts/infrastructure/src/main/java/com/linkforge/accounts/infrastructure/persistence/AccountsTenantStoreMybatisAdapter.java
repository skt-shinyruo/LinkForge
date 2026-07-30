package com.linkforge.accounts.infrastructure.persistence;

import com.linkforge.accounts.application.port.AccountsTenantStore;
import com.linkforge.accounts.infrastructure.persistence.entity.TenantEntity;
import com.linkforge.accounts.infrastructure.persistence.mapper.TenantMapper;
import org.springframework.stereotype.Component;

/**
 * 租户存储端口的 MyBatis 适配器。
 *
 * <p>不独立声明事务，写入随调用方事务提交或回滚。查询未命中返回 {@code null}，
 * {@code null} 写入参数为无操作；数据库唯一约束和写入异常保持向上传播。</p>
 */
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
