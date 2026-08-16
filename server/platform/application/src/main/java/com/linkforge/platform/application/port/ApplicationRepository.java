package com.linkforge.platform.application.port;

import com.linkforge.platform.domain.Application;

import java.util.List;
import java.util.Optional;

/**
 * 应用聚合的持久化端口。
 *
 * <p>租户侧读取必须显式携带 {@code tenantId}，不得在适配器中退化为只按应用标识查询。
 * 事务边界由应用服务控制；端口实现不应自行提交。应用标识全局唯一，
 * {@code (tenantId, applicationKey)} 在存储层也必须保持唯一。</p>
 */
public interface ApplicationRepository {

    /**
     * 插入新应用；标识或租户内应用键冲突时传播持久化唯一约束异常。
     */
    void insert(Application application);

    /**
     * 在租户边界内按标识查询应用。
     */
    Optional<Application> findByTenantIdAndId(long tenantId, long applicationId);

    /**
     * 列出租户全部应用，不隐式过滤状态。
     */
    List<Application> listByTenantId(long tenantId);

    /**
     * 跨租户列出全部应用，仅供已经在外层完成平台级授权的管理流程使用。
     */
    List<Application> listAll();
}
