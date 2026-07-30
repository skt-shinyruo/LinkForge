package com.linkforge.accounts.application.port;

import java.time.LocalDateTime;

/**
 * 租户账户信息的持久化端口。
 *
 * <p>写操作加入调用方当前事务；新增不是幂等操作，唯一约束冲突应向上抛出。</p>
 */
public interface AccountsTenantStore {

    /**
     * 新增租户。创建和更新时间可留空，由数据库默认值生成。
     */
    void insert(TenantData tenant);

    /**
     * 按标识读取租户。
     *
     * @return 记录不存在时返回 {@code null}
     */
    TenantData findById(Long tenantId);

    /**
     * 租户持久化快照。{@code createdAt} 与 {@code updatedAt} 均以 UTC 解释，创建入参可为
     * {@code null} 以使用数据库默认时间。
     */
    record TenantData(Long id, String name, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
