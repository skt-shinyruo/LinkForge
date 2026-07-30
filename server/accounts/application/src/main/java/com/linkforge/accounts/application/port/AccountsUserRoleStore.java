package com.linkforge.accounts.application.port;

import java.util.List;

/**
 * 用户角色关联的持久化端口。
 *
 * <p>用户与角色编码共同构成唯一关联，新增操作加入调用方当前事务且不保证幂等；重复关联导致的
 * 约束冲突应向上抛出。查询必须以空列表表达无结果，避免调用方把 {@code null} 误解为未加载。</p>
 */
public interface AccountsUserRoleStore {

    /**
     * 新增一条用户角色关联。
     */
    void insert(UserRoleData userRole);

    /**
     * 新增一条用户角色关联的便捷入口，事务和重复键语义与 {@link #insert(UserRoleData)} 相同。
     */
    default void insert(long userId, String roleCode) {
        insert(new UserRoleData(userId, roleCode));
    }

    /**
     * 查询指定用户的全部显式角色。
     *
     * @return 无关联时返回空列表，不在持久化层补默认角色
     */
    List<UserRoleData> findAllByUserId(Long userId);

    /**
     * 批量查询用户的显式角色。空或 {@code null} 的标识列表应直接返回空列表。
     *
     * @return 无关联时返回空列表
     */
    List<UserRoleData> findAllByUserIdIn(List<Long> userIds);

    /**
     * 用户与稳定角色编码组成的持久化关联；二者共同构成唯一身份，均不应为 {@code null}。
     */
    record UserRoleData(Long userId, String roleCode) {
    }
}
