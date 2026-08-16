package com.linkforge.platform.infrastructure.persistence;

import com.linkforge.platform.application.port.LegacyBindingLockRepository;
import com.linkforge.platform.infrastructure.persistence.mapper.LegacyBindingLockMapper;
import org.springframework.stereotype.Component;

/** 通过 Platform 自有锁行串行化同一租户的 legacy binding reconcile。 */
@Component
public class LegacyBindingLockRepositoryMybatisAdapter implements LegacyBindingLockRepository {

    private final LegacyBindingLockMapper mapper;

    public LegacyBindingLockRepositoryMybatisAdapter(LegacyBindingLockMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void lockTenant(long tenantId) {
        int affected = mapper.lockTenant(tenantId);
        if (affected < 0) {
            throw new IllegalStateException("Legacy binding tenant lock unavailable");
        }
    }
}
