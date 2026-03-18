package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountsUserRoleStore;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.accounts.domain.Roles;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.security.TenantGuard;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UserAdminService {

    private static final Set<String> USER_ROLE_WHITELIST = Set.of(
            Roles.TENANT_ADMIN,
            Roles.USER
    );

    private final SnowflakeIdGenerator idGenerator;
    private final AccountsUserStore userStore;
    private final AccountsUserRoleStore userRoleStore;
    private final PasswordEncoder passwordEncoder;
    private final TenantGuard tenantGuard;

    public UserAdminService(
            SnowflakeIdGenerator idGenerator,
            AccountsUserStore userStore,
            AccountsUserRoleStore userRoleStore,
            PasswordEncoder passwordEncoder,
            TenantGuard tenantGuard
    ) {
        this.idGenerator = idGenerator;
        this.userStore = userStore;
        this.userRoleStore = userRoleStore;
        this.passwordEncoder = passwordEncoder;
        this.tenantGuard = tenantGuard;
    }

    public List<UserDto> list(long tenantId) {
        tenantGuard.requireCurrentTenant(tenantId);
        List<AccountsUserStore.UserData> users = userStore.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        if (users.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = users.stream().map(AccountsUserStore.UserData::id).toList();
        Map<Long, Set<String>> rolesByUserId = loadRolesByUserIds(userIds);

        return users.stream()
                .map(u -> new UserDto(
                        u.id(),
                        u.tenantId(),
                        u.email(),
                        u.status(),
                        rolesByUserId.getOrDefault(u.id(), Set.of())
                ))
                .toList();
    }

    @Transactional
    public UserDto create(long tenantId, CreateUserRequest req) {
        tenantGuard.requireCurrentTenant(tenantId);
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
                passwordEncoder.encode(req.password()),
                AccountsConstants.STATUS_ACTIVE,
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

        return new UserDto(userId, tenantId, user.email(), user.status(), roles);
    }

    @Transactional
    public UserDto disable(long tenantId, long userId) {
        tenantGuard.requireCurrentTenant(tenantId);
        AccountsUserStore.UserData user = requireUserInTenant(tenantId, userId);
        if (!AccountsConstants.STATUS_DISABLED.equals(user.status())) {
            userStore.update(withStatus(user, AccountsConstants.STATUS_DISABLED));
        }
        Set<String> roles = loadRolesByUserId(userId);
        return new UserDto(user.id(), tenantId, user.email(), AccountsConstants.STATUS_DISABLED, roles);
    }

    @Transactional
    public UserDto enable(long tenantId, long userId) {
        tenantGuard.requireCurrentTenant(tenantId);
        AccountsUserStore.UserData user = requireUserInTenant(tenantId, userId);
        if (!AccountsConstants.STATUS_ACTIVE.equals(user.status())) {
            userStore.update(withStatus(user, AccountsConstants.STATUS_ACTIVE));
        }
        Set<String> roles = loadRolesByUserId(userId);
        return new UserDto(user.id(), tenantId, user.email(), AccountsConstants.STATUS_ACTIVE, roles);
    }

    @Transactional
    public UserDto resetPassword(long tenantId, long userId, String newPassword) {
        tenantGuard.requireCurrentTenant(tenantId);
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
                passwordEncoder.encode(newPassword),
                user.status(),
                user.createdAt(),
                user.updatedAt()
        );
        userStore.update(updated);

        Set<String> roles = loadRolesByUserId(userId);
        return new UserDto(user.id(), tenantId, user.email(), user.status(), roles);
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
        return roles.isEmpty() ? Set.of(Roles.USER) : roles;
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
                user.createdAt(),
                user.updatedAt()
        );
    }

    private static boolean tenantIdEquals(Long actualTenantId, long expectedTenantId) {
        return actualTenantId != null && actualTenantId == expectedTenantId;
    }

    public record CreateUserRequest(String email, String password, Set<String> roles) {
    }

    public record UserDto(long id, long tenantId, String email, String status, Set<String> roles) {
    }

    private static Set<String> normalizeAndValidateRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of(Roles.USER);
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

        return normalized.isEmpty() ? Set.of(Roles.USER) : Set.copyOf(normalized);
    }
}
