package com.linkforge.platform.infrastructure.persistence;

import com.linkforge.platform.application.port.DomainRepository;
import com.linkforge.platform.domain.Domain;
import com.linkforge.platform.domain.DomainScope;
import com.linkforge.platform.domain.DomainStatus;
import com.linkforge.platform.domain.TargetTrustClass;
import com.linkforge.platform.infrastructure.persistence.entity.DomainEntity;
import com.linkforge.platform.infrastructure.persistence.mapper.DomainMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class DomainRepositoryMybatisAdapter implements DomainRepository {

    private final DomainMapper mapper;

    public DomainRepositoryMybatisAdapter(DomainMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(Domain domain) {
        mapper.insert(toEntity(domain));
    }

    @Override
    public Optional<Domain> findByTenantIdAndId(long tenantId, long domainId) {
        return Optional.ofNullable(mapper.findByTenantIdAndId(tenantId, domainId)).map(this::toDomain);
    }

    @Override
    public Optional<Domain> findByTenantIdAndHostname(long tenantId, String hostname) {
        return Optional.ofNullable(mapper.findByTenantIdAndHostname(tenantId, hostname)).map(this::toDomain);
    }

    @Override
    public void authorizeApplicationUse(long applicationId, long domainId) {
        mapper.insertAuthorization(applicationId, domainId);
    }

    @Override
    public boolean isApplicationAuthorizedForDomain(long applicationId, long domainId) {
        return mapper.countAuthorization(applicationId, domainId) > 0;
    }

    @Override
    public List<Domain> listByTenantId(long tenantId) {
        return mapper.listByTenantId(tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Domain> listUsableByApplication(long tenantId, long applicationId) {
        return mapper.listUsableByApplication(tenantId, applicationId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Domain> listAll() {
        return mapper.listAll().stream().map(this::toDomain).toList();
    }

    private static DomainEntity toEntity(Domain domain) {
        DomainEntity entity = new DomainEntity();
        entity.setId(domain.id());
        entity.setTenantId(domain.tenantId());
        entity.setApplicationId(domain.applicationId());
        entity.setHostname(domain.hostname());
        entity.setScope(domain.scope().name());
        entity.setStatus(domain.status().name());
        entity.setTrustClass(domain.trustClass().name());
        entity.setCreatedAt(domain.createdAt());
        entity.setUpdatedAt(domain.updatedAt());
        return entity;
    }

    private Domain toDomain(DomainEntity entity) {
        return new Domain(
                entity.getId(),
                entity.getTenantId(),
                entity.getApplicationId(),
                entity.getHostname(),
                DomainScope.valueOf(entity.getScope()),
                DomainStatus.valueOf(entity.getStatus()),
                TargetTrustClass.valueOf(entity.getTrustClass()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
