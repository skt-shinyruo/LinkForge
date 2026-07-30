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

/**
 * 域名及应用-域名授权关系的 MyBatis 持久化适配器。
 *
 * <p>适配器不自行开启事务，也不把并发冲突吞掉。域名写入是普通 {@code INSERT}；
 * {@code hostname} 受全局唯一约束保护，因此并发创建相同规范化 hostname 时只会有一个成功。
 * 授权关系同样依赖 {@code (application_id, domain_id)} 复合主键去重，重复授权会报告约束冲突，
 * 不是幂等成功。</p>
 *
 * <p>领域枚举按名称持久化；读取到未知枚举值会快速失败，以免把无法解释的数据静默映射为
 * 其他权限或信任等级。</p>
 */
@Component
public class DomainRepositoryMybatisAdapter implements DomainRepository {

    private final DomainMapper mapper;

    public DomainRepositoryMybatisAdapter(DomainMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 插入域名快照。租户共享域名允许 {@code applicationId} 为 {@code null}；作用域与该字段
     * 的组合不变量由领域/应用层校验，持久化层仅原样落库。
     */
    @Override
    public void insert(Domain domain) {
        mapper.insert(toEntity(domain));
    }

    /**
     * 在租户边界内按域名 ID 查询，不存在时返回空 {@link Optional}。
     */
    @Override
    public Optional<Domain> findByTenantIdAndId(long tenantId, long domainId) {
        return Optional.ofNullable(mapper.findByTenantIdAndId(tenantId, domainId)).map(this::toDomain);
    }

    /**
     * 在租户边界内按 hostname 查询；数据库全局唯一约束保证最多返回一条记录。
     */
    @Override
    public Optional<Domain> findByTenantIdAndHostname(long tenantId, String hostname) {
        return Optional.ofNullable(mapper.findByTenantIdAndHostname(tenantId, hostname)).map(this::toDomain);
    }

    /**
     * 创建应用对租户共享域名的授权关系。
     *
     * <p>这是单条插入而非 upsert；调用方需先校验应用、域名、租户和作用域，且重复调用会因
     * 复合主键冲突而失败。该写入参与调用方事务，不单独提交。</p>
     */
    @Override
    public void authorizeApplicationUse(long applicationId, long domainId) {
        mapper.insertAuthorization(applicationId, domainId);
    }

    /**
     * 查询授权关系当前是否存在。该读取不加锁，仅表示语句执行时的数据库快照，不能替代写入时
     * 的唯一约束或事务内重新校验。
     */
    @Override
    public boolean isApplicationAuthorizedForDomain(long applicationId, long domainId) {
        return mapper.countAuthorization(applicationId, domainId) > 0;
    }

    /**
     * 返回租户内全部域名，包含非 ACTIVE 状态，顺序为创建时间和 ID 倒序。
     */
    @Override
    public List<Domain> listByTenantId(long tenantId) {
        return mapper.listByTenantId(tenantId).stream().map(this::toDomain).toList();
    }

    /**
     * 返回应用当前可用的 ACTIVE 域名：应用专属域名必须直接绑定该应用，租户共享域名必须存在
     * 显式授权。租户条件同时约束域名记录，防止跨租户域名进入结果。
     */
    @Override
    public List<Domain> listUsableByApplication(long tenantId, long applicationId) {
        return mapper.listUsableByApplication(tenantId, applicationId).stream().map(this::toDomain).toList();
    }

    /**
     * 返回所有租户的域名快照，不附加状态、权限或租户过滤，供已授权的控制面调用。
     */
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
