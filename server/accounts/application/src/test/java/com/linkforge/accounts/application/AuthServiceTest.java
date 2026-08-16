package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountStatusCache;
import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.accounts.application.port.AccountsTenantStore;
import com.linkforge.accounts.application.port.AccountsTokenIssuer;
import com.linkforge.accounts.application.port.AccountsUserRoleStore;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.security.StandardRoles;
import com.linkforge.foundation.tx.PostCommitHookPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

        AuthResult result = service.register("Tenant A", "admin@example.com", "password123");

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

        AuthResult result = service.login("member@example.com", "password123");

        verify(tokenIssuer).issueToken(100L, 200L, "member@example.com", Set.of(StandardRoles.USER), 0);
        assertThat(result.principal().getRoles()).containsExactly(StandardRoles.USER);
    }

    @Test
    void logout_shouldAdvanceTokenVersionAtomically_andEvictOnlyAfterCommit() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        AccountsTenantStore tenantStore = mock(AccountsTenantStore.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountsUserRoleStore userRoleStore = mock(AccountsUserRoleStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        AccountsTokenIssuer tokenIssuer = mock(AccountsTokenIssuer.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);
        CapturingPostCommitHook postCommitHook = new CapturingPostCommitHook();
        when(passwordHasher.encode(anyString())).thenReturn("dummy-hash");

        AuthService service = new AuthService(
                idGenerator,
                tenantStore,
                userStore,
                userRoleStore,
                passwordHasher,
                tokenIssuer,
                statusCache,
                postCommitHook
        );
        when(userStore.findById(100L)).thenReturn(new AccountsUserStore.UserData(
                100L, 200L, "member@example.com", "hash", AccountsConstants.STATUS_ACTIVE, 7, null, null
        ));
        when(userStore.incrementTokenVersion(100L)).thenReturn(true);

        service.logout(100L);

        verify(userStore).incrementTokenVersion(100L);
        verify(userStore, never()).update(org.mockito.ArgumentMatchers.any());
        verify(statusCache, never()).evictUserStatus(100L);

        postCommitHook.runCaptured();

        verify(statusCache).evictUserStatus(100L);
        verify(statusCache).evictTenantStatus(200L);
    }

    @Test
    void login_shouldPerformPasswordWork_andReturnSameErrorForMissingOrDisabledUser() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        AccountsTenantStore tenantStore = mock(AccountsTenantStore.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountsUserRoleStore userRoleStore = mock(AccountsUserRoleStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        AccountsTokenIssuer tokenIssuer = mock(AccountsTokenIssuer.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);
        when(passwordHasher.encode(anyString())).thenReturn("dummy-hash");

        AuthService service = new AuthService(
                idGenerator,
                tenantStore,
                userStore,
                userRoleStore,
                passwordHasher,
                tokenIssuer,
                statusCache
        );

        assertThatThrownBy(() -> service.login("missing@example.com", "password123"))
                .isInstanceOf(com.linkforge.contract.api.BusinessException.class)
                .satisfies(ex -> assertThat(((com.linkforge.contract.api.BusinessException) ex).getErrorCode())
                        .isEqualTo(com.linkforge.contract.accounts.AccountsErrorCode.INVALID_CREDENTIALS));
        verify(passwordHasher).matches("password123", "dummy-hash");

        AccountsUserStore.UserData disabled = new AccountsUserStore.UserData(
                100L, 200L, "disabled@example.com", "stored-hash", AccountsConstants.STATUS_DISABLED, 0, null, null
        );
        AccountsTenantStore.TenantData activeTenant = new AccountsTenantStore.TenantData(
                200L, "Tenant A", AccountsConstants.STATUS_ACTIVE, null, null
        );
        when(userStore.findFirstByEmail("disabled@example.com")).thenReturn(disabled);
        when(tenantStore.findById(200L)).thenReturn(activeTenant);

        assertThatThrownBy(() -> service.login("disabled@example.com", "password123"))
                .isInstanceOf(com.linkforge.contract.api.BusinessException.class)
                .satisfies(ex -> assertThat(((com.linkforge.contract.api.BusinessException) ex).getErrorCode())
                        .isEqualTo(com.linkforge.contract.accounts.AccountsErrorCode.INVALID_CREDENTIALS));
        verify(passwordHasher).matches("password123", "stored-hash");
    }

    private static final class CapturingPostCommitHook implements PostCommitHookPort {
        private final AtomicReference<Runnable> action = new AtomicReference<>();

        @Override
        public void run(Runnable action) {
            this.action.set(action);
        }

        void runCaptured() {
            Runnable captured = action.get();
            if (captured == null) {
                throw new AssertionError("expected a post-commit callback");
            }
            captured.run();
        }
    }
}
