package com.linkforge.shortlink.infrastructure.persistence.repository;

import com.linkforge.shortlink.application.port.ShortLinkOwnershipBackfillRepository;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkCommandMapper;
import org.springframework.stereotype.Repository;

/**
 * 旧短链所有权字段回填的 MyBatis 适配器。
 *
 * <p>回填只更新指定租户中 {@code application_id} 与 {@code domain_id} 同时为空的行，已有任一 scope
 * 的数据不会被覆盖。方法返回实际更新行数，事务边界和重复执行策略由调用方控制；相同参数重复执行
 * 在数据层自然收敛为零行更新。</p>
 */
@Repository
public class ShortLinkOwnershipBackfillRepositoryMybatisAdapter implements ShortLinkOwnershipBackfillRepository {

    private final ShortLinkCommandMapper commandMapper;

    public ShortLinkOwnershipBackfillRepositoryMybatisAdapter(ShortLinkCommandMapper commandMapper) {
        this.commandMapper = commandMapper;
    }

    @Override
    public int backfillTenant(long tenantId, long applicationId, long domainId) {
        return commandMapper.backfillOwnershipByTenant(tenantId, applicationId, domainId);
    }
}
