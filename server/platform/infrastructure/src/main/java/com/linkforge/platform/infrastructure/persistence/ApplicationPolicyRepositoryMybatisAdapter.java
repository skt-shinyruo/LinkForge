package com.linkforge.platform.infrastructure.persistence;

import com.linkforge.platform.application.port.ApplicationPolicyRepository;
import com.linkforge.platform.domain.ApplicationPolicy;
import com.linkforge.platform.infrastructure.persistence.entity.ApplicationPolicyEntity;
import com.linkforge.platform.infrastructure.persistence.mapper.ApplicationPolicyMapper;
import org.springframework.stereotype.Component;

/**
 * 应用默认策略的一对一持久化适配器。
 *
 * <p>写入为普通 {@code INSERT}：{@code application_id} 是主键，重复写入不会覆盖已有策略，
 * 而会由数据库报告唯一约束冲突。适配器不声明事务，通常与应用创建操作处于同一调用方事务中，
 * 从而使应用、策略和额度要么整体提交，要么整体回滚。</p>
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
