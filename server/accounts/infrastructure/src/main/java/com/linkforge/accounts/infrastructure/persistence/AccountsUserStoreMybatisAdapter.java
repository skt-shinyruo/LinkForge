package com.linkforge.accounts.infrastructure.persistence;

import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.accounts.infrastructure.persistence.entity.UserEntity;
import com.linkforge.accounts.infrastructure.persistence.mapper.UserMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户存储端口的 MyBatis 适配器。
 *
 * <p>事务边界由调用方应用服务控制，本类仅完成端口数据与持久化实体之间的无损映射。
 * 单条查询未命中返回 {@code null}，列表无结果返回不可变空列表；{@code null} 写入参数为无操作。
 * 密码字段始终按已编码摘要传递，本层不接收、还原或记录明文凭据。</p>
 */
@Component
public class AccountsUserStoreMybatisAdapter implements AccountsUserStore {

    private final UserMapper userMapper;

    public AccountsUserStoreMybatisAdapter(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void insert(UserData user) {
        if (user == null) {
            return;
        }
        userMapper.insert(toEntity(user));
    }

    @Override
    public UserData findById(Long userId) {
        return toUser(userMapper.findById(userId));
    }

    @Override
    public UserData findFirstByEmail(String email) {
        return toUser(userMapper.findFirstByEmail(email));
    }

    @Override
    public List<UserData> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId) {
        List<UserEntity> list = userMapper.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream().map(AccountsUserStoreMybatisAdapter::toUser).toList();
    }

    @Override
    public boolean incrementTokenVersion(Long userId) {
        return userId != null && userMapper.incrementTokenVersion(userId) > 0;
    }

    @Override
    public boolean updatePasswordHashAndIncrementTokenVersion(Long tenantId, Long userId, String passwordHash) {
        return tenantId != null
                && userId != null
                && passwordHash != null
                && userMapper.updatePasswordHashAndIncrementTokenVersion(tenantId, userId, passwordHash) > 0;
    }

    @Override
    public boolean updateStatus(Long tenantId, Long userId, String status) {
        return tenantId != null
                && userId != null
                && status != null
                && userMapper.updateStatus(tenantId, userId, status) > 0;
    }

    @Override
    public void lockTenantForUserAdministration(Long tenantId) {
        if (tenantId != null) {
            userMapper.lockTenantForUserAdministration(tenantId);
        }
    }

    private static UserEntity toEntity(UserData user) {
        UserEntity entity = new UserEntity();
        entity.setId(user.id());
        entity.setTenantId(user.tenantId());
        entity.setEmail(user.email());
        entity.setPasswordHash(user.passwordHash());
        entity.setStatus(user.status());
        entity.setTokenVersion(user.tokenVersion());
        entity.setCreatedAt(user.createdAt());
        entity.setUpdatedAt(user.updatedAt());
        return entity;
    }

    private static UserData toUser(UserEntity entity) {
        if (entity == null) {
            return null;
        }
        return new UserData(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getStatus(),
                entity.getTokenVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
