package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountStatusCache;
import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.accounts.application.port.AccountsUserRoleStore;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.security.StandardRoles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 租户管理员维护用户、角色和凭据状态的应用服务。
 *
 * <p>所有单用户读写都校验用户属于请求租户，跨租户 ID 与不存在资源统一返回 not-found。可授予角色
 * 仅限 {@code TENANT_ADMIN} 和 {@code USER}，空角色集合默认普通用户。邮箱唯一性由预查询提供友好
 * 错误，并由数据库约束处理并发竞争。</p>
 *
 * <p>禁用操作拒绝管理员禁用自己，并在当前快照上保证至少保留一个启用的租户管理员；该计数未加
 * 悲观锁，因此它是应用层保护而非并发事务下的严格全局约束。用户状态或凭据变化后会尽力驱逐认证
 * 状态缓存。驱逐与数据库更新位于同一事务；驱逐失败或提交窗口内并发请求重建旧快照时，权限变化
 * 最迟由状态缓存的短 TTL 重新校准。</p>
 */
@Service
public class UserAdminService {

    private static final Set<String> USER_ROLE_WHITELIST = Set.of(
            StandardRoles.TENANT_ADMIN,
            StandardRoles.USER
    );

    private final SnowflakeIdGenerator idGenerator;
    private final AccountsUserStore userStore;
    private final AccountsUserRoleStore userRoleStore;
    private final AccountsPasswordHasher passwordHasher;
    private final AccountStatusCache statusCache;

    public UserAdminService(
            SnowflakeIdGenerator idGenerator,
            AccountsUserStore userStore,
            AccountsUserRoleStore userRoleStore,
            AccountsPasswordHasher passwordHasher,
            AccountStatusCache statusCache
    ) {
        this.idGenerator = idGenerator;
        this.userStore = userStore;
        this.userRoleStore = userRoleStore;
        this.passwordHasher = passwordHasher;
        this.statusCache = statusCache;
    }

    /**
     * 列出租户用户及显式角色记录，不跨租户聚合数据。
     *
     * <p>结果沿用存储端口的创建时间倒序；没有角色记录的用户在列表中返回空集合，不在此读取路径
     * 推断默认角色。</p>
     */
    public List<UserResult> list(long tenantId) {
        List<AccountsUserStore.UserData> users = userStore.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        if (users.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = users.stream().map(AccountsUserStore.UserData::id).toList();
        Map<Long, Set<String>> rolesByUserId = loadRolesByUserIds(userIds);

        return users.stream()
                .map(u -> new UserResult(
                        u.id(),
                        u.tenantId(),
                        u.email(),
                        u.status(),
                        rolesByUserId.getOrDefault(u.id(), Set.of())
                ))
                .toList();
    }

    /**
     * 在一个事务内创建启用用户并写入规范化后的角色集合。
     *
     * @throws BusinessException 命令为空、角色无效或邮箱已存在
     */
    @Transactional
    public UserResult create(long tenantId, CreateUserCommand req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求不能为空");
        }

        Set<String> roles = normalizeAndValidateRoles(req.roles());

        if (userStore.findFirstByEmail(req.email()) != null) {
            throw new BusinessException(AccountsErrorCode.EMAIL_ALREADY_EXISTS);
        }

        long userId = idGenerator.nextId();
        AccountsUserStore.UserData user = new AccountsUserStore.UserData(
                userId,
                tenantId,
                req.email(),
                passwordHasher.encode(req.password()),
                AccountsConstants.STATUS_ACTIVE,
                0,
                null,
                null
        );
        try {
            userStore.insert(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AccountsErrorCode.EMAIL_ALREADY_EXISTS);
        }

        for (String role : roles) {
            userRoleStore.insert(new AccountsUserRoleStore.UserRoleData(userId, role));
        }

        return new UserResult(userId, tenantId, user.email(), user.status(), roles);
    }

    /**
     * 禁用租户内用户。
     *
     * <p>重复禁用按幂等成功处理；当前操作者不能禁用自己，且普通执行路径不会移除最后一个启用的
     * 租户管理员。状态写入后驱逐用户认证缓存。</p>
     */
    @Transactional
    public UserResult disable(long tenantId, long actorUserId, long userId) {
        AccountsUserStore.UserData user = requireUserInTenant(tenantId, userId);
        Set<String> roles = loadRolesByUserId(userId);
        requireDisableAllowed(tenantId, actorUserId, user, roles);
        if (!AccountsConstants.STATUS_DISABLED.equals(user.status())) {
            userStore.update(withStatus(user, AccountsConstants.STATUS_DISABLED));
        }
        statusCache.evictUserStatus(userId);
        return new UserResult(user.id(), tenantId, user.email(), AccountsConstants.STATUS_DISABLED, roles);
    }

    /**
     * 启用租户内用户；重复启用按幂等成功处理，并驱逐可能陈旧的状态缓存。
     *
     * <p>启用不会递增 {@code tokenVersion}。因此禁用前签发且尚未过期的 JWT，在用户重新启用并且
     * 状态缓存重新校准后可以恢复使用；需要永久撤销旧令牌时应同时重置密码或注销。</p>
     */
    @Transactional
    public UserResult enable(long tenantId, long userId) {
        AccountsUserStore.UserData user = requireUserInTenant(tenantId, userId);
        if (!AccountsConstants.STATUS_ACTIVE.equals(user.status())) {
            userStore.update(withStatus(user, AccountsConstants.STATUS_ACTIVE));
        }
        statusCache.evictUserStatus(userId);
        Set<String> roles = loadRolesByUserId(userId);
        return new UserResult(user.id(), tenantId, user.email(), AccountsConstants.STATUS_ACTIVE, roles);
    }

    /**
     * 重置租户内用户密码并递增 {@code tokenVersion}。
     *
     * <p>递增版本会使此前签发的 JWT 在状态缓存重新校准后失效；新密码只以哈希形式持久化。密码
     * 长度边界为 8 到 64 个 Java 字符。</p>
     */
    @Transactional
    public UserResult resetPassword(long tenantId, long userId, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码不能为空");
        }
        if (newPassword.length() < 8 || newPassword.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码长度需为 8-64");
        }

        AccountsUserStore.UserData user = requireUserInTenant(tenantId, userId);
        AccountsUserStore.UserData updated = new AccountsUserStore.UserData(
                user.id(),
                user.tenantId(),
                user.email(),
                passwordHasher.encode(newPassword),
                user.status(),
                nextTokenVersion(user),
                user.createdAt(),
                user.updatedAt()
        );
        userStore.update(updated);
        statusCache.evictUserStatus(userId);

        Set<String> roles = loadRolesByUserId(userId);
        return new UserResult(user.id(), tenantId, user.email(), user.status(), roles);
    }

    private Map<Long, Set<String>> loadRolesByUserIds(List<Long> userIds) {
        Map<Long, Set<String>> map = new java.util.HashMap<>();
        for (AccountsUserRoleStore.UserRoleData r : userRoleStore.findAllByUserIdIn(userIds)) {
            if (r == null) {
                continue;
            }
            map.computeIfAbsent(r.userId(), k -> new java.util.HashSet<>()).add(r.roleCode());
        }
        return map;
    }

    private Set<String> loadRolesByUserId(long userId) {
        Set<String> roles = userRoleStore.findAllByUserId(userId).stream()
                .filter(r -> r != null)
                .map(AccountsUserRoleStore.UserRoleData::roleCode)
                .collect(java.util.stream.Collectors.toSet());
        return roles.isEmpty() ? Set.of(StandardRoles.USER) : roles;
    }

    private AccountsUserStore.UserData requireUserInTenant(long tenantId, long userId) {
        AccountsUserStore.UserData user = userStore.findById(userId);
        if (user == null || !tenantIdEquals(user.tenantId(), tenantId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private static AccountsUserStore.UserData withStatus(AccountsUserStore.UserData user, String status) {
        return new AccountsUserStore.UserData(
                user.id(),
                user.tenantId(),
                user.email(),
                user.passwordHash(),
                status,
                user.tokenVersion(),
                user.createdAt(),
                user.updatedAt()
        );
    }

    private void requireDisableAllowed(
            long tenantId,
            long actorUserId,
            AccountsUserStore.UserData user,
            Set<String> roles
    ) {
        if (user == null || user.id() == null) {
            return;
        }
        if (actorUserId == user.id()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能禁用当前管理员");
        }
        if (!AccountsConstants.STATUS_ACTIVE.equals(user.status()) || !roles.contains(StandardRoles.TENANT_ADMIN)) {
            return;
        }

        List<AccountsUserStore.UserData> tenantUsers = userStore.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        if (tenantUsers.isEmpty()) {
            return;
        }

        List<Long> userIds = tenantUsers.stream()
                .map(AccountsUserStore.UserData::id)
                .toList();
        Map<Long, Set<String>> rolesByUserId = loadRolesByUserIds(userIds);
        long activeTenantAdminCount = tenantUsers.stream()
                .filter(u -> AccountsConstants.STATUS_ACTIVE.equals(u.status()))
                .filter(u -> rolesByUserId.getOrDefault(u.id(), Set.of()).contains(StandardRoles.TENANT_ADMIN))
                .count();
        if (activeTenantAdminCount <= 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少保留一个启用中的租户管理员");
        }
    }

    private static int nextTokenVersion(AccountsUserStore.UserData user) {
        if (user == null || user.tokenVersion() == null) {
            return 1;
        }
        return user.tokenVersion() + 1;
    }

    private static boolean tenantIdEquals(Long actualTenantId, long expectedTenantId) {
        return actualTenantId != null && actualTenantId == expectedTenantId;
    }

    private static Set<String> normalizeAndValidateRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of(StandardRoles.USER);
        }

        Set<String> normalized = new HashSet<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "roles 不能包含空值");
            }
            String r = role.trim();
            if (!USER_ROLE_WHITELIST.contains(r)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "未知角色: " + r);
            }
            normalized.add(r);
        }

        return normalized.isEmpty() ? Set.of(StandardRoles.USER) : Set.copyOf(normalized);
    }
}
