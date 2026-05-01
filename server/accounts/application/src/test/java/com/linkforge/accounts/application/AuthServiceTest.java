package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountStatusCache;
import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.accounts.application.port.AccountsTenantStore;
import com.linkforge.accounts.application.port.AccountsTokenIssuer;
import com.linkforge.accounts.application.port.AccountsUserRoleStore;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.security.StandardRoles;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Test
    void register_shouldGrantTenantAdminRoleFromStandardRoles() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        AccountsTenantStore tenantStore = mock(AccountsTenantStore.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountsUserRoleStore userRoleStore = mock(AccountsUserRoleStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        AccountsTokenIssuer tokenIssuer = mock(AccountsTokenIssuer.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);

        AuthService service = new AuthService(
                idGenerator,
                tenantStore,
                userStore,
                userRoleStore,
                passwordHasher,
                tokenIssuer,
                statusCache
        );

        when(idGenerator.nextId()).thenReturn(200L, 100L);
        when(passwordHasher.encode("password123")).thenReturn("hash");
        when(tokenIssuer.issueToken(100L, 200L, "admin@example.com", Set.of(StandardRoles.TENANT_ADMIN), 0))
                .thenReturn("jwt-token");

        AuthService.AuthResult result = service.register("Tenant A", "admin@example.com", "password123");

        verify(userRoleStore).insert(new AccountsUserRoleStore.UserRoleData(100L, StandardRoles.TENANT_ADMIN));
        verify(tokenIssuer).issueToken(100L, 200L, "admin@example.com", Set.of(StandardRoles.TENANT_ADMIN), 0);
        assertThat(result.principal().getRoles()).containsExactly(StandardRoles.TENANT_ADMIN);
    }

    @Test
    void login_shouldDefaultToUserRoleFromStandardRoles() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        AccountsTenantStore tenantStore = mock(AccountsTenantStore.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountsUserRoleStore userRoleStore = mock(AccountsUserRoleStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        AccountsTokenIssuer tokenIssuer = mock(AccountsTokenIssuer.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);

        AuthService service = new AuthService(
                idGenerator,
                tenantStore,
                userStore,
                userRoleStore,
                passwordHasher,
                tokenIssuer,
                statusCache
        );

        AccountsUserStore.UserData user = new AccountsUserStore.UserData(
                100L,
                200L,
                "member@example.com",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                0,
                null,
                null
        );
        AccountsTenantStore.TenantData tenant = new AccountsTenantStore.TenantData(
                200L,
                "Tenant A",
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );
        when(userStore.findFirstByEmail("member@example.com")).thenReturn(user);
        when(tenantStore.findById(200L)).thenReturn(tenant);
        when(passwordHasher.matches("password123", "hash")).thenReturn(true);
        when(userRoleStore.findAllByUserId(100L)).thenReturn(List.of());
        when(tokenIssuer.issueToken(100L, 200L, "member@example.com", Set.of(StandardRoles.USER), 0))
                .thenReturn("jwt-token");

        AuthService.AuthResult result = service.login("member@example.com", "password123");

        verify(tokenIssuer).issueToken(100L, 200L, "member@example.com", Set.of(StandardRoles.USER), 0);
        assertThat(result.principal().getRoles()).containsExactly(StandardRoles.USER);
    }

    @Test
    void login_shouldFailWithTenantDisabledWhenTenantInactive() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        AccountsTenantStore tenantStore = mock(AccountsTenantStore.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountsUserRoleStore userRoleStore = mock(AccountsUserRoleStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        AccountsTokenIssuer tokenIssuer = mock(AccountsTokenIssuer.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);

        AuthService service = new AuthService(
                idGenerator,
                tenantStore,
                userStore,
                userRoleStore,
                passwordHasher,
                tokenIssuer,
                statusCache
        );

        AccountsUserStore.UserData user = new AccountsUserStore.UserData(
                100L,
                200L,
                "member@example.com",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                0,
                null,
                null
        );
        AccountsTenantStore.TenantData tenant = new AccountsTenantStore.TenantData(
                200L,
                "Tenant A",
                AccountsConstants.STATUS_DISABLED,
                null,
                null
        );
        when(userStore.findFirstByEmail("member@example.com")).thenReturn(user);
        when(tenantStore.findById(200L)).thenReturn(tenant);

        assertThatThrownBy(() -> service.login("member@example.com", "password123"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(AccountsErrorCode.TENANT_DISABLED));
    }

    @Test
    void login_shouldFailWithUserDisabledWhenUserInactive() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        AccountsTenantStore tenantStore = mock(AccountsTenantStore.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountsUserRoleStore userRoleStore = mock(AccountsUserRoleStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        AccountsTokenIssuer tokenIssuer = mock(AccountsTokenIssuer.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);

        AuthService service = new AuthService(
                idGenerator,
                tenantStore,
                userStore,
                userRoleStore,
                passwordHasher,
                tokenIssuer,
                statusCache
        );

        AccountsUserStore.UserData user = new AccountsUserStore.UserData(
                100L,
                200L,
                "member@example.com",
                "hash",
                AccountsConstants.STATUS_DISABLED,
                0,
                null,
                null
        );
        AccountsTenantStore.TenantData tenant = new AccountsTenantStore.TenantData(
                200L,
                "Tenant A",
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        );
        when(userStore.findFirstByEmail("member@example.com")).thenReturn(user);
        when(tenantStore.findById(200L)).thenReturn(tenant);

        assertThatThrownBy(() -> service.login("member@example.com", "password123"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(AccountsErrorCode.USER_DISABLED));
    }

    @Test
    void logout_shouldIncrementTokenVersionThroughDomainModel() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        AccountsTenantStore tenantStore = mock(AccountsTenantStore.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountsUserRoleStore userRoleStore = mock(AccountsUserRoleStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        AccountsTokenIssuer tokenIssuer = mock(AccountsTokenIssuer.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);

        AuthService service = new AuthService(
                idGenerator,
                tenantStore,
                userStore,
                userRoleStore,
                passwordHasher,
                tokenIssuer,
                statusCache
        );

        LocalDateTime createdAt = LocalDateTime.parse("2026-04-30T10:15:30");
        LocalDateTime updatedAt = LocalDateTime.parse("2026-04-30T10:20:30");
        AccountsUserStore.UserData user = new AccountsUserStore.UserData(
                100L,
                200L,
                "member@example.com",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                null,
                createdAt,
                updatedAt
        );
        when(userStore.findById(100L)).thenReturn(user);

        service.logout(100L);

        ArgumentCaptor<AccountsUserStore.UserData> userCaptor = ArgumentCaptor.forClass(AccountsUserStore.UserData.class);
        verify(userStore).update(userCaptor.capture());
        assertThat(userCaptor.getValue()).isEqualTo(new AccountsUserStore.UserData(
                100L,
                200L,
                "member@example.com",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                1,
                createdAt,
                updatedAt
        ));
        verify(statusCache).evictUserStatus(100L);
    }
}
