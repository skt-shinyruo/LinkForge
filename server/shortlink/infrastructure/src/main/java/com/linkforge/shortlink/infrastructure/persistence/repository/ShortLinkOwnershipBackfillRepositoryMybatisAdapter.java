package com.linkforge.shortlink.infrastructure.persistence.repository;

import com.linkforge.shortlink.application.port.ShortLinkOwnershipBackfillRepository;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkCommandMapper;
import org.springframework.stereotype.Repository;

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
