package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountStatusCache;
import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.accounts.application.port.AccountsUserRoleStore;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.security.StandardRoles;
import com.linkforge.foundation.tx.PostCommitHookPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>禁用操作拒绝管理员禁用自己，并通过租户协调行锁串行化“至少保留一个启用中的租户管理员”
 * 检查与状态写入。用户状态或凭据成功提交后驱逐认证状态缓存；回滚不会驱逐，提交后的驱逐失败会
 * 记录告警且不改变已提交事实。</p>
 */
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private static final Set<String> USER_ROLE_WHITELIST = Set.of(
            StandardRoles.TENANT_ADMIN,
            StandardRoles.USER
    );

    private final SnowflakeIdGenerator idGenerator;
    private final AccountsUserStore userStore;
    private final AccountsUserRoleStore userRoleStore;
    private final AccountsPasswordHasher passwordHasher;
    private final AccountStatusCache statusCache;
    private final PostCommitHookPort postCommitHookPort;

    public UserAdminService(
            SnowflakeIdGenerator idGenerator,
            AccountsUserStore userStore,
            AccountsUserRoleStore userRoleStore,
            AccountsPasswordHasher passwordHasher,
            AccountStatusCache statusCache,
            PostCommitHookPort postCommitHookPort
    ) {
        this.idGenerator = idGenerator;
        this.userStore = userStore;
        this.userRoleStore = userRoleStore;
        this.passwordHasher = passwordHasher;
        this.statusCache = statusCache;
        this.postCommitHookPort = postCommitHookPort;
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
        userStore.lockTenantForUserAdministration(tenantId);
        AccountsUserStore.UserData user = requireUserInTenant(tenantId, userId);
        Set<String> roles = loadRolesByUserId(userId);
        requireDisableAllowed(tenantId, actorUserId, user, roles);
        if (!AccountsConstants.STATUS_DISABLED.equals(user.status())) {
            requireStatusUpdate(userStore.updateStatus(tenantId, userId, AccountsConstants.STATUS_DISABLED));
        }
        evictUserStatusAfterCommit(userId);
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
            requireStatusUpdate(userStore.updateStatus(tenantId, userId, AccountsConstants.STATUS_ACTIVE));
        }
        evictUserStatusAfterCommit(userId);
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
        String passwordHash = passwordHasher.encode(newPassword);
        requireStatusUpdate(userStore.updatePasswordHashAndIncrementTokenVersion(tenantId, userId, passwordHash));
        evictUserStatusAfterCommit(userId);

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

    private static void requireStatusUpdate(boolean updated) {
        if (!updated) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    private void evictUserStatusAfterCommit(long userId) {
        postCommitHookPort.run(() -> {
            try {
                if (!statusCache.evictUserStatus(userId)) {
                    log.warn("account status cache eviction incomplete after commit: userId={}", userId);
                }
            } catch (RuntimeException ex) {
                log.warn("account status cache eviction failed after commit: userId={}, err={}",
                        userId, ex.getMessage());
            }
        });
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
