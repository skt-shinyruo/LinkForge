package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountStatusCache;
import com.linkforge.accounts.application.port.AccountsTenantStore;
import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.accounts.application.port.AccountsTokenIssuer;
import com.linkforge.accounts.application.port.AccountsUserRoleStore;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.accounts.domain.AccountStatusPolicy;
import com.linkforge.accounts.domain.AccountUser;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.accounts.domain.EmailAddress;
import com.linkforge.accounts.domain.RoleAssignment;
import com.linkforge.accounts.domain.RoleCode;
import com.linkforge.accounts.domain.RolePolicy;
import com.linkforge.accounts.domain.Tenant;
import com.linkforge.accounts.domain.TenantName;
import com.linkforge.accounts.domain.TokenVersion;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.security.StandardRoles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final SnowflakeIdGenerator idGenerator;
    private final AccountsTenantStore tenantStore;
    private final AccountsUserStore userStore;
    private final AccountsUserRoleStore userRoleStore;
    private final AccountsPasswordHasher passwordHasher;
    private final AccountsTokenIssuer tokenIssuer;
    private final AccountStatusCache statusCache;
    private final AccountStatusPolicy accountStatusPolicy = new AccountStatusPolicy();
    private final RolePolicy rolePolicy = new RolePolicy();

    public AuthService(
            SnowflakeIdGenerator idGenerator,
            AccountsTenantStore tenantStore,
            AccountsUserStore userStore,
            AccountsUserRoleStore userRoleStore,
            AccountsPasswordHasher passwordHasher,
            AccountsTokenIssuer tokenIssuer,
            AccountStatusCache statusCache
    ) {
        this.idGenerator = idGenerator;
        this.tenantStore = tenantStore;
        this.userStore = userStore;
        this.userRoleStore = userRoleStore;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.statusCache = statusCache;
    }

    @Transactional
    public AuthResult register(String tenantName, String email, String rawPassword) {
        if (userStore.findFirstByEmail(email) != null) {
            throw new BusinessException(AccountsErrorCode.EMAIL_ALREADY_EXISTS);
        }

        long tenantId = idGenerator.nextId();
        tenantStore.insert(new AccountsTenantStore.TenantData(
                tenantId,
                tenantName,
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        ));

        long userId = idGenerator.nextId();
        AccountsUserStore.UserData user = new AccountsUserStore.UserData(
                userId,
                tenantId,
                email,
                passwordHasher.encode(rawPassword),
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

        userRoleStore.insert(new AccountsUserRoleStore.UserRoleData(userId, StandardRoles.TENANT_ADMIN));

        Set<String> roles = Set.of(StandardRoles.TENANT_ADMIN);
        String token = tokenIssuer.issueToken(userId, tenantId, email, roles, 0);
        return new AuthResult(token, new AuthPrincipal(userId, tenantId, email, roles, 0));
    }

    public AuthResult login(String email, String rawPassword) {
        AccountsUserStore.UserData userData = userStore.findFirstByEmail(email);
        if (userData == null) {
            throw new BusinessException(AccountsErrorCode.INVALID_CREDENTIALS);
        }

        AccountsTenantStore.TenantData tenantData = tenantStore.findById(userData.tenantId());
        if (tenantData == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "租户不存在");
        }

        Tenant tenant = toTenant(tenantData);
        AccountUser user = toAccountUser(userData);
        if (!accountStatusPolicy.canAuthenticate(tenant, user)) {
            if (!tenant.active()) {
                throw new BusinessException(AccountsErrorCode.TENANT_DISABLED);
            }
            throw new BusinessException(AccountsErrorCode.USER_DISABLED);
        }
        if (!passwordHasher.matches(rawPassword, userData.passwordHash())) {
            throw new BusinessException(AccountsErrorCode.INVALID_CREDENTIALS);
        }

        Set<String> roles = rolePolicy.effectiveRoles(userRoleStore.findAllByUserId(user.id()).stream()
                .filter(r -> r != null)
                .map(r -> RoleAssignment.of(user.id(), RoleCode.of(r.roleCode())))
                .collect(Collectors.toUnmodifiableSet()));

        int tokenVersion = user.tokenVersion().value();
        String token = tokenIssuer.issueToken(user.id(), user.tenantId(), userData.email(), roles, tokenVersion);
        return new AuthResult(token, new AuthPrincipal(user.id(), user.tenantId(), userData.email(), roles, tokenVersion));
    }

    @Transactional
    public void logout(long userId) {
        if (userId <= 0) {
            return;
        }
        AccountsUserStore.UserData userData = userStore.findById(userId);
        if (userData == null) {
            return;
        }
        AccountUser user = toAccountUser(userData).logout();
        userStore.update(withTokenVersion(userData, user.tokenVersion()));
        statusCache.evictUserStatus(userId);
    }

    private static Tenant toTenant(AccountsTenantStore.TenantData tenant) {
        return Tenant.rehydrate(tenant.id(), TenantName.of(tenant.name()), tenant.status());
    }

    private static AccountUser toAccountUser(AccountsUserStore.UserData user) {
        return AccountUser.rehydrate(
                user.id(),
                user.tenantId(),
                EmailAddress.of(user.email()),
                user.status(),
                TokenVersion.of(user.tokenVersion())
        );
    }

    private static AccountsUserStore.UserData withTokenVersion(
            AccountsUserStore.UserData user,
            TokenVersion tokenVersion
    ) {
        return new AccountsUserStore.UserData(
                user.id(),
                user.tenantId(),
                user.email(),
                user.passwordHash(),
                user.status(),
                tokenVersion.value(),
                user.createdAt(),
                user.updatedAt()
        );
    }

    public record AuthResult(String token, AuthPrincipal principal) {
    }
}
