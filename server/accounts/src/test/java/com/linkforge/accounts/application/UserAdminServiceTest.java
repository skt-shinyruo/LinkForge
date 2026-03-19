package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountStatusCache;
import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.accounts.application.port.AccountsUserRoleStore;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.accounts.domain.Roles;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.runtime.security.TenantGuard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserAdminServiceTest {

    @Test
    void constructor_shouldDependOnApplicationPorts_insteadOfPasswordEncoder() {
        Constructor<?> constructor = UserAdminService.class.getDeclaredConstructors()[0];

        assertThat(Arrays.stream(constructor.getParameterTypes()).map(Class::getName))
                .contains("com.linkforge.accounts.application.port.AccountsPasswordHasher")
                .contains("com.linkforge.accounts.application.port.AccountStatusCache")
                .doesNotContain("org.springframework.security.crypto.password.PasswordEncoder");
    }

    @Test
    void create_shouldRejectUnknownRoleCodes() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountsUserRoleStore userRoleStore = mock(AccountsUserRoleStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        TenantGuard tenantGuard = mock(TenantGuard.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);

        UserAdminService service = new UserAdminService(
                idGenerator,
                userStore,
                userRoleStore,
                passwordHasher,
                tenantGuard,
                statusCache
        );

        UserAdminService.CreateUserRequest req = new UserAdminService.CreateUserRequest(
                "u@example.com",
                "password123",
                Set.of("UNKNOWN_ROLE")
        );

        assertThatThrownBy(() -> service.create(1L, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                });

        verify(userStore, never()).insert(any());
        verifyNoInteractions(userRoleStore);
    }

    @Test
    void resetPassword_shouldIncrementTokenVersion() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountsUserRoleStore userRoleStore = mock(AccountsUserRoleStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        TenantGuard tenantGuard = mock(TenantGuard.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);

        UserAdminService service = new UserAdminService(
                idGenerator,
                userStore,
                userRoleStore,
                passwordHasher,
                tenantGuard,
                statusCache
        );

        AccountsUserStore.UserData existing = new AccountsUserStore.UserData(
                100L,
                200L,
                "member@example.com",
                "old-hash",
                AccountsConstants.STATUS_ACTIVE,
                7,
                null,
                null
        );
        when(userStore.findById(100L)).thenReturn(existing);
        when(userRoleStore.findAllByUserId(100L)).thenReturn(List.of(new AccountsUserRoleStore.UserRoleData(100L, Roles.USER)));
        when(passwordHasher.encode("new-password123")).thenReturn("new-hash");

        UserAdminService.UserDto result = service.resetPassword(200L, 100L, "new-password123");

        assertThat(result.id()).isEqualTo(100L);

        ArgumentCaptor<AccountsUserStore.UserData> captor = ArgumentCaptor.forClass(AccountsUserStore.UserData.class);
        verify(userStore).update(captor.capture());
        assertThat(captor.getValue().passwordHash()).isEqualTo("new-hash");
        assertThat(captor.getValue().tokenVersion()).isEqualTo(8);
        verify(statusCache).evictUserStatus(100L);
    }

    @Test
    void disable_shouldRejectSelfDisable_forTenantAdmin() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountsUserRoleStore userRoleStore = mock(AccountsUserRoleStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        TenantGuard tenantGuard = mock(TenantGuard.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);

        UserAdminService service = new UserAdminService(
                idGenerator,
                userStore,
                userRoleStore,
                passwordHasher,
                tenantGuard,
                statusCache
        );

        AccountsUserStore.UserData existing = new AccountsUserStore.UserData(
                100L,
                200L,
                "admin@example.com",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                0,
                null,
                null
        );
        when(userStore.findById(100L)).thenReturn(existing);
        when(userRoleStore.findAllByUserId(100L)).thenReturn(List.of(new AccountsUserRoleStore.UserRoleData(100L, Roles.TENANT_ADMIN)));

        assertThatThrownBy(() -> service.disable(200L, 100L, 100L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(userStore, never()).update(any());
    }

    @Test
    void disable_shouldRejectDisablingLastActiveTenantAdmin() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountsUserRoleStore userRoleStore = mock(AccountsUserRoleStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        TenantGuard tenantGuard = mock(TenantGuard.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);

        UserAdminService service = new UserAdminService(
                idGenerator,
                userStore,
                userRoleStore,
                passwordHasher,
                tenantGuard,
                statusCache
        );

        AccountsUserStore.UserData onlyAdmin = new AccountsUserStore.UserData(
                100L,
                200L,
                "admin@example.com",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                0,
                null,
                null
        );
        AccountsUserStore.UserData disabledAdmin = new AccountsUserStore.UserData(
                101L,
                200L,
                "disabled-admin@example.com",
                "hash",
                AccountsConstants.STATUS_DISABLED,
                0,
                null,
                null
        );
        AccountsUserStore.UserData member = new AccountsUserStore.UserData(
                102L,
                200L,
                "member@example.com",
                "hash",
                AccountsConstants.STATUS_ACTIVE,
                0,
                null,
                null
        );

        when(userStore.findById(100L)).thenReturn(onlyAdmin);
        when(userStore.findAllByTenantIdOrderByCreatedAtDesc(200L)).thenReturn(List.of(onlyAdmin, disabledAdmin, member));
        when(userRoleStore.findAllByUserId(100L)).thenReturn(List.of(new AccountsUserRoleStore.UserRoleData(100L, Roles.TENANT_ADMIN)));
        when(userRoleStore.findAllByUserIdIn(List.of(100L, 101L, 102L))).thenReturn(List.of(
                new AccountsUserRoleStore.UserRoleData(100L, Roles.TENANT_ADMIN),
                new AccountsUserRoleStore.UserRoleData(101L, Roles.TENANT_ADMIN),
                new AccountsUserRoleStore.UserRoleData(102L, Roles.USER)
        ));

        assertThatThrownBy(() -> service.disable(200L, 999L, 100L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(userStore, never()).update(any());
    }
}
