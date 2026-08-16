package com.linkforge.platform.infrastructure.persistence;

import com.linkforge.platform.application.port.ApplicationQuotaRepository;
import com.linkforge.platform.domain.ApplicationQuota;
import com.linkforge.platform.infrastructure.persistence.entity.ApplicationQuotaEntity;
import com.linkforge.platform.infrastructure.persistence.mapper.ApplicationQuotaMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 应用月度额度的一对一 MyBatis 持久化适配器。
 *
 * <p>该适配器只负责额度配置，不负责用量扣减或并发计数。常规创建使用普通 {@code INSERT}；
 * legacy reconcile 使用显式 upsert 覆盖为当前兼容额度。事务边界由调用方定义。</p>
 */
@Component
public class ApplicationQuotaRepositoryMybatisAdapter implements ApplicationQuotaRepository {

    private final ApplicationQuotaMapper mapper;

    public ApplicationQuotaRepositoryMybatisAdapter(ApplicationQuotaMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 写入应用的初始额度，时间字段交由数据库生成。
     */
    @Override
    public void insert(ApplicationQuota quota) {
        mapper.insert(toEntity(quota));
    }

    @Override
    public void upsert(ApplicationQuota quota) {
        mapper.upsert(toEntity(quota));
    }

    private static ApplicationQuotaEntity toEntity(ApplicationQuota quota) {
        ApplicationQuotaEntity entity = new ApplicationQuotaEntity();
        entity.setApplicationId(quota.applicationId());
        entity.setMonthlyLinkLimit(quota.monthlyLinkLimit());
        entity.setMonthlyClickLimit(quota.monthlyClickLimit());
        entity.setCreatedAt(quota.createdAt());
        entity.setUpdatedAt(quota.updatedAt());
        return entity;
    }

    /**
     * 按应用 ID 读取额度；无记录时返回空 {@link Optional}。
     *
     * <p>表约束要求两个额度列非空；为兼容异常历史数据或宽松测试替身，读取到 SQL
     * {@code NULL} 时仍归一为 {@code 0}，避免自动拆箱异常。此兼容行为不代表写入允许空值。</p>
     */
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
