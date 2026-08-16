package com.linkforge.platform.infrastructure.persistence;

import com.linkforge.platform.application.port.ApplicationPolicyRepository;
import com.linkforge.platform.domain.ApplicationPolicy;
import com.linkforge.platform.infrastructure.persistence.entity.ApplicationPolicyEntity;
import com.linkforge.platform.infrastructure.persistence.mapper.ApplicationPolicyMapper;
import org.springframework.stereotype.Component;

/**
 * 应用默认策略的一对一持久化适配器。
 *
 * <p>常规创建使用普通 {@code INSERT} 并保留唯一约束错误；legacy reconcile 使用显式 upsert
 * 收敛当前策略。适配器不声明事务，两种写入都参与调用方事务。</p>
 */
@Component
public class ApplicationPolicyRepositoryMybatisAdapter implements ApplicationPolicyRepository {

    private final ApplicationPolicyMapper mapper;

    public ApplicationPolicyRepositoryMybatisAdapter(ApplicationPolicyMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 持久化策略初始值；枚举按名称写入，时间字段由数据库默认值维护。
     */
    @Override
    public void insert(ApplicationPolicy policy) {
        mapper.insert(toEntity(policy));
    }

    @Override
    public void upsert(ApplicationPolicy policy) {
        mapper.upsert(toEntity(policy));
    }

    private static ApplicationPolicyEntity toEntity(ApplicationPolicy policy) {
        ApplicationPolicyEntity entity = new ApplicationPolicyEntity();
        entity.setApplicationId(policy.applicationId());
        entity.setDefaultDomainScope(policy.defaultDomainScope().name());
        entity.setDefaultRedirectStatusCode(policy.defaultRedirectStatusCode());
        entity.setPreviewEnabled(policy.previewEnabled());
        entity.setCreatedAt(policy.createdAt());
        entity.setUpdatedAt(policy.updatedAt());
        return entity;
    }
}
