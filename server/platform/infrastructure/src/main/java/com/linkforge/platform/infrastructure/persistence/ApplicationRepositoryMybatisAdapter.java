package com.linkforge.platform.infrastructure.persistence;

import com.linkforge.platform.application.port.ApplicationRepository;
import com.linkforge.platform.domain.Application;
import com.linkforge.platform.infrastructure.persistence.entity.ApplicationEntity;
import com.linkforge.platform.infrastructure.persistence.mapper.ApplicationMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 应用聚合的 MyBatis 持久化适配器。
 *
 * <p>本适配器不自行开启事务，所有读写均参与调用方已有的 Spring 事务。写入使用普通
 * {@code INSERT}，既不是 upsert 也不具备幂等性；应用主键重复，或同一租户内
 * {@code applicationKey} 重复时，数据库唯一约束会通过数据访问异常暴露给应用层处理。</p>
 *
 * <p>按租户查询的方法始终同时使用 {@code tenantId} 过滤，避免仅凭全局 ID 形成越租户读取。
 * {@link #listAll()} 则是明确的跨租户控制面能力，调用方必须在进入仓储前完成授权。</p>
 */
@Component
public class ApplicationRepositoryMybatisAdapter implements ApplicationRepository {

    private final ApplicationMapper mapper;

    public ApplicationRepositoryMybatisAdapter(ApplicationMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 插入完整应用快照；数据库生成 {@code created_at}/{@code updated_at}，传入领域对象中的
     * 对应时间不会写入。
     */
    @Override
    public void insert(Application application) {
        mapper.insert(toEntity(application));
    }

    /**
     * 在租户边界内按 ID 查询；不存在时返回空 {@link Optional}，不会以 {@code null} 表示缺失。
     */
    @Override
    public Optional<Application> findByTenantIdAndId(long tenantId, long applicationId) {
        return Optional.ofNullable(mapper.findByTenantIdAndId(tenantId, applicationId)).map(this::toDomain);
    }

    /**
     * 返回指定租户的应用快照，顺序由 SQL 固定为创建时间和 ID 倒序。
     */
    @Override
    public List<Application> listByTenantId(long tenantId) {
        return mapper.listByTenantId(tenantId).stream().map(this::toDomain).toList();
    }

    /**
     * 返回所有租户的应用快照，不在持久化层附加授权或租户过滤。
     */
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
