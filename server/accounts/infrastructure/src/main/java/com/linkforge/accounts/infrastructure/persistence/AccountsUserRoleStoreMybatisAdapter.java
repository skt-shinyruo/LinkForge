package com.linkforge.accounts.infrastructure.persistence;

import com.linkforge.accounts.application.port.AccountsUserRoleStore;
import com.linkforge.accounts.infrastructure.persistence.entity.UserRoleEntity;
import com.linkforge.accounts.infrastructure.persistence.entity.UserRoleId;
import com.linkforge.accounts.infrastructure.persistence.mapper.UserRoleMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户角色关联存储端口的 MyBatis 适配器。
 *
 * <p>写入参与调用方事务，唯一性和并发冲突由数据库约束及上层异常映射负责。
 * 批量查询对 {@code null}/空 ID 集合直接返回不可变空列表，避免生成无意义的 IN 条件；
 * Mapper 无结果同样规范化为空列表。</p>
 */
@Component
public class AccountsUserRoleStoreMybatisAdapter implements AccountsUserRoleStore {

    private final UserRoleMapper userRoleMapper;

    public AccountsUserRoleStoreMybatisAdapter(UserRoleMapper userRoleMapper) {
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public void insert(UserRoleData userRole) {
        if (userRole == null) {
            return;
        }
        userRoleMapper.insert(new UserRoleEntity(new UserRoleId(userRole.userId(), userRole.roleCode())));
    }

    @Override
    public List<UserRoleData> findAllByUserId(Long userId) {
        List<UserRoleEntity> list = userRoleMapper.findAllByUserId(userId);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream().map(AccountsUserRoleStoreMybatisAdapter::toData).toList();
    }

    @Override
    public List<UserRoleData> findAllByUserIdIn(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<UserRoleEntity> list = userRoleMapper.findAllByUserIdIn(userIds);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream().map(AccountsUserRoleStoreMybatisAdapter::toData).toList();
    }

    private static UserRoleData toData(UserRoleEntity entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }
        return new UserRoleData(entity.getId().getUserId(), entity.getId().getRoleCode());
    }
}
