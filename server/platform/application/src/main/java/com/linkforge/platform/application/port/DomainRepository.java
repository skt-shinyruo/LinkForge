package com.linkforge.platform.application.port;

import com.linkforge.platform.domain.Domain;

import java.util.List;
import java.util.Optional;

/**
 * 域名及应用域名授权关系的持久化端口。
 *
 * <p>域名实体读取必须遵守租户边界；应用服务负责主机名规范化、状态和范围规则，仓储负责持久化唯一性。
 * 授权关系是独立记录，其事务与调用方命令一致。</p>
 */
public interface DomainRepository {

    /**
     * 插入域名；标识或主机名冲突时传播存储唯一约束异常。
     */
    void insert(Domain domain);

    /**
     * 在租户边界内按标识查询域名。
     */
    Optional<Domain> findByTenantIdAndId(long tenantId, long domainId);

    /**
     * 插入应用对共享域名的显式授权。
     *
     * <p>该操作不是 upsert；重复关系可能触发唯一约束异常。</p>
     */
    void authorizeApplicationUse(long applicationId, long domainId);

    /**
     * 判断显式授权关系是否存在；专属域名的绑定关系不由该方法推导。
     */
    boolean isApplicationAuthorizedForDomain(long applicationId, long domainId);

    /**
     * 在租户边界内按规范化主机名查询域名。
     */
    Optional<Domain> findByTenantIdAndHostname(long tenantId, String hostname);

    /**
     * 按全局唯一主机名读取，仅供 Platform 在创建前识别被其他租户占用的确定性冲突。
     */
    Optional<Domain> findByHostname(String hostname);

    /**
     * 列出租户全部域名，不隐式过滤状态或授权关系。
     */
    List<Domain> listByTenantId(long tenantId);

    /**
     * 列出应用当前可用的启用域名：已绑定的专属域名及已显式授权的共享域名。
     */
    List<Domain> listUsableByApplication(long tenantId, long applicationId);

    /**
     * 跨租户列出全部域名，仅供已经在外层完成平台级授权的管理流程使用。
     */
    List<Domain> listAll();
}
