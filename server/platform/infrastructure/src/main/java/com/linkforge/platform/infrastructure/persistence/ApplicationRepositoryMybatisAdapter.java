package com.linkforge.platform.infrastructure.persistence;

import com.linkforge.platform.application.port.ApplicationRepository;
import com.linkforge.platform.domain.Application;
import com.linkforge.platform.infrastructure.persistence.entity.ApplicationEntity;
import com.linkforge.platform.infrastructure.persistence.mapper.ApplicationMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ApplicationRepositoryMybatisAdapter implements ApplicationRepository {

    private final ApplicationMapper mapper;

    public ApplicationRepositoryMybatisAdapter(ApplicationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(Application application) {
        mapper.insert(toEntity(application));
    }

    @Override
    public Optional<Application> findByTenantIdAndId(long tenantId, long applicationId) {
        return Optional.ofNullable(mapper.findByTenantIdAndId(tenantId, applicationId)).map(this::toDomain);
    }

    @Override
    public Optional<Application> findByTenantIdAndApplicationKey(long tenantId, String applicationKey) {
        return Optional.ofNullable(mapper.findByTenantIdAndApplicationKey(tenantId, applicationKey)).map(this::toDomain);
    }

    @Override
    public List<Application> listByTenantId(long tenantId) {
        return mapper.listByTenantId(tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Application> listAll() {
        return mapper.listAll().stream().map(this::toDomain).toList();
    }

    private static ApplicationEntity toEntity(Application application) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setId(application.id());
        entity.setTenantId(application.tenantId());
        entity.setApplicationKey(application.applicationKey());
        entity.setDisplayName(application.displayName());
        entity.setStatus(application.status());
        entity.setCreatedAt(application.createdAt());
        entity.setUpdatedAt(application.updatedAt());
        return entity;
    }

    private Application toDomain(ApplicationEntity entity) {
        return new Application(
                entity.getId(),
                entity.getTenantId(),
                entity.getApplicationKey(),
                entity.getDisplayName(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
