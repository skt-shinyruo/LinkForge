package com.linkforge.platform.infrastructure.persistence;

import com.linkforge.platform.application.port.ApplicationPolicyRepository;
import com.linkforge.platform.domain.ApplicationPolicy;
import com.linkforge.platform.infrastructure.persistence.entity.ApplicationPolicyEntity;
import com.linkforge.platform.infrastructure.persistence.mapper.ApplicationPolicyMapper;
import org.springframework.stereotype.Component;

@Component
public class ApplicationPolicyRepositoryMybatisAdapter implements ApplicationPolicyRepository {

    private final ApplicationPolicyMapper mapper;

    public ApplicationPolicyRepositoryMybatisAdapter(ApplicationPolicyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(ApplicationPolicy policy) {
        ApplicationPolicyEntity entity = new ApplicationPolicyEntity();
        entity.setApplicationId(policy.applicationId());
        entity.setDefaultDomainScope(policy.defaultDomainScope().name());
        entity.setDefaultRedirectStatusCode(policy.defaultRedirectStatusCode());
        entity.setPreviewEnabled(policy.previewEnabled());
        entity.setCreatedAt(policy.createdAt());
        entity.setUpdatedAt(policy.updatedAt());
        mapper.insert(entity);
    }
}
