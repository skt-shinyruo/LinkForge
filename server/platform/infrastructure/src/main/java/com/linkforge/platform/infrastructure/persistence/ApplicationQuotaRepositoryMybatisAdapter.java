package com.linkforge.platform.infrastructure.persistence;

import com.linkforge.platform.application.port.ApplicationQuotaRepository;
import com.linkforge.platform.domain.ApplicationQuota;
import com.linkforge.platform.infrastructure.persistence.entity.ApplicationQuotaEntity;
import com.linkforge.platform.infrastructure.persistence.mapper.ApplicationQuotaMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ApplicationQuotaRepositoryMybatisAdapter implements ApplicationQuotaRepository {

    private final ApplicationQuotaMapper mapper;

    public ApplicationQuotaRepositoryMybatisAdapter(ApplicationQuotaMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(ApplicationQuota quota) {
        ApplicationQuotaEntity entity = new ApplicationQuotaEntity();
        entity.setApplicationId(quota.applicationId());
        entity.setMonthlyLinkLimit(quota.monthlyLinkLimit());
        entity.setMonthlyClickLimit(quota.monthlyClickLimit());
        entity.setCreatedAt(quota.createdAt());
        entity.setUpdatedAt(quota.updatedAt());
        mapper.insert(entity);
    }

    @Override
    public Optional<ApplicationQuota> findByApplicationId(long applicationId) {
        return Optional.ofNullable(mapper.findByApplicationId(applicationId))
                .map(entity -> new ApplicationQuota(
                        entity.getApplicationId(),
                        entity.getMonthlyLinkLimit() == null ? 0L : entity.getMonthlyLinkLimit(),
                        entity.getMonthlyClickLimit() == null ? 0L : entity.getMonthlyClickLimit(),
                        entity.getCreatedAt(),
                        entity.getUpdatedAt()
                ));
    }
}
