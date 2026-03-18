package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountsTenantStore;
import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.accounts.application.port.AccountsTokenIssuer;
import com.linkforge.accounts.application.port.AccountsUserRoleStore;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.accounts.domain.Roles;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.security.AuthPrincipal;
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

    public AuthService(
            SnowflakeIdGenerator idGenerator,
            AccountsTenantStore tenantStore,
            AccountsUserStore userStore,
            AccountsUserRoleStore userRoleStore,
            AccountsPasswordHasher passwordHasher,
            AccountsTokenIssuer tokenIssuer
    ) {
        this.idGenerator = idGenerator;
        this.tenantStore = tenantStore;
        this.userStore = userStore;
        this.userRoleStore = userRoleStore;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
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
                null,
                null
        );
        try {
            userStore.insert(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AccountsErrorCode.EMAIL_ALREADY_EXISTS);
        }

        userRoleStore.insert(new AccountsUserRoleStore.UserRoleData(userId, Roles.TENANT_ADMIN));

        Set<String> roles = Set.of(Roles.TENANT_ADMIN);
        String token = tokenIssuer.issueToken(userId, tenantId, email, roles);
        return new AuthResult(token, new AuthPrincipal(userId, tenantId, email, roles));
    }

    public AuthResult login(String email, String rawPassword) {
        AccountsUserStore.UserData user = userStore.findFirstByEmail(email);
        if (user == null) {
            throw new BusinessException(AccountsErrorCode.INVALID_CREDENTIALS);
        }

        AccountsTenantStore.TenantData tenant = tenantStore.findById(user.tenantId());
        if (tenant == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "租户不存在");
        }

        if (!AccountsConstants.STATUS_ACTIVE.equals(tenant.status())) {
            throw new BusinessException(AccountsErrorCode.TENANT_DISABLED);
        }
        if (!AccountsConstants.STATUS_ACTIVE.equals(user.status())) {
            throw new BusinessException(AccountsErrorCode.USER_DISABLED);
        }
        if (!passwordHasher.matches(rawPassword, user.passwordHash())) {
            throw new BusinessException(AccountsErrorCode.INVALID_CREDENTIALS);
        }

        Set<String> roles = userRoleStore.findAllByUserId(user.id()).stream()
                .filter(r -> r != null)
                .map(AccountsUserRoleStore.UserRoleData::roleCode)
                .collect(Collectors.toUnmodifiableSet());

        if (roles.isEmpty()) {
            roles = Set.of(Roles.USER);
        }

        String token = tokenIssuer.issueToken(user.id(), user.tenantId(), user.email(), roles);
        return new AuthResult(token, new AuthPrincipal(user.id(), user.tenantId(), user.email(), roles));
    }

    public record AuthResult(String token, AuthPrincipal principal) {
    }
}
