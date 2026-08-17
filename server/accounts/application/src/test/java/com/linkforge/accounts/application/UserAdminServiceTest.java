package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountStatusCache;
import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.accounts.application.port.AccountsUserRoleStore;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.security.StandardRoles;
import com.linkforge.foundation.tx.PostCommitHookPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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
                .doesNotContain("com.linkforge.foundation.runtime.security.TenantGuard")
                .doesNotContain("org.springframework.security.crypto.password.PasswordEncoder");
    }

    @Test
    void create_shouldRejectUnknownRoleCodes() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountsUserRoleStore userRoleStore = mock(AccountsUserRoleStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);

        UserAdminService service = new UserAdminService(
                idGenerator,
                userStore,
                userRoleStore,
                passwordHasher,
                statusCache,
                Runnable::run
        );

        CreateUserCommand req = new CreateUserCommand(
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
    void resetPassword_shouldUseNarrowAtomicCommand_andEvictOnlyAfterCommit() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountsUserRoleStore userRoleStore = mock(AccountsUserRoleStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);
        CapturingPostCommitHook postCommitHook = new CapturingPostCommitHook();

        UserAdminService service = new UserAdminService(
                idGenerator,
                userStore,
                userRoleStore,
                passwordHasher,
                statusCache,
                postCommitHook
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
        when(userRoleStore.findAllByUserId(100L)).thenReturn(List.of(new AccountsUserRoleStore.UserRoleData(100L, StandardRoles.USER)));
        when(passwordHasher.encode("new-password123")).thenReturn("new-hash");
        when(userStore.updatePasswordHashAndIncrementTokenVersion(200L, 100L, "new-hash")).thenReturn(true);

        UserResult result = service.resetPassword(200L, 100L, "new-password123");

        assertThat(result.id()).isEqualTo(100L);

        verify(userStore).updatePasswordHashAndIncrementTokenVersion(200L, 100L, "new-hash");
        verify(statusCache, never()).evictUserStatus(100L);

        postCommitHook.runCaptured();

        verify(statusCache).evictUserStatus(100L);
    }

    @Test
    void disable_shouldRejectSelfDisable_forTenantAdmin() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountsUserRoleStore userRoleStore = mock(AccountsUserRoleStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);

        UserAdminService service = new UserAdminService(
                idGenerator,
                userStore,
                userRoleStore,
                passwordHasher,
                statusCache,
                Runnable::run
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
        when(userRoleStore.findAllByUserId(100L)).thenReturn(List.of(new AccountsUserRoleStore.UserRoleData(100L, StandardRoles.TENANT_ADMIN)));

        assertThatThrownBy(() -> service.disable(200L, 100L, 100L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

    }

    @Test
    void disable_shouldRejectDisablingLastActiveTenantAdmin() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountsUserRoleStore userRoleStore = mock(AccountsUserRoleStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);

        UserAdminService service = new UserAdminService(
                idGenerator,
                userStore,
                userRoleStore,
                passwordHasher,
                statusCache,
                Runnable::run
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
        when(userRoleStore.findAllByUserId(100L)).thenReturn(List.of(new AccountsUserRoleStore.UserRoleData(100L, StandardRoles.TENANT_ADMIN)));
        when(userRoleStore.findAllByUserIdIn(List.of(100L, 101L, 102L))).thenReturn(List.of(
                new AccountsUserRoleStore.UserRoleData(100L, StandardRoles.TENANT_ADMIN),
                new AccountsUserRoleStore.UserRoleData(101L, StandardRoles.TENANT_ADMIN),
                new AccountsUserRoleStore.UserRoleData(102L, StandardRoles.USER)
        ));

        assertThatThrownBy(() -> service.disable(200L, 999L, 100L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

    }

    @Test
    void disableAndEnable_shouldUseNarrowStatusCommands() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountsUserRoleStore userRoleStore = mock(AccountsUserRoleStore.class);
        AccountsPasswordHasher passwordHasher = mock(AccountsPasswordHasher.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);
        CapturingPostCommitHook postCommitHook = new CapturingPostCommitHook();
        UserAdminService service = new UserAdminService(
                idGenerator, userStore, userRoleStore, passwordHasher, statusCache, postCommitHook
        );
        AccountsUserStore.UserData activeMember = new AccountsUserStore.UserData(
                100L, 200L, "member@example.com", "hash", AccountsConstants.STATUS_ACTIVE, 9, null, null
        );
        AccountsUserStore.UserData disabledMember = new AccountsUserStore.UserData(
                100L, 200L, "member@example.com", "hash", AccountsConstants.STATUS_DISABLED, 9, null, null
        );
        when(userStore.findById(100L)).thenReturn(activeMember, disabledMember);
        when(userRoleStore.findAllByUserId(100L)).thenReturn(
                List.of(new AccountsUserRoleStore.UserRoleData(100L, StandardRoles.USER))
        );
        when(userStore.updateStatus(200L, 100L, AccountsConstants.STATUS_DISABLED)).thenReturn(true);
        when(userStore.updateStatus(200L, 100L, AccountsConstants.STATUS_ACTIVE)).thenReturn(true);

        service.disable(200L, 999L, 100L);

        verify(userStore).lockTenantForUserAdministration(200L);
        verify(userStore).updateStatus(200L, 100L, AccountsConstants.STATUS_DISABLED);
        verify(statusCache, never()).evictUserStatus(100L);
        postCommitHook.runCaptured();
        verify(statusCache).evictUserStatus(100L);

        postCommitHook.clear();
        service.enable(200L, 100L);
        verify(userStore).updateStatus(200L, 100L, AccountsConstants.STATUS_ACTIVE);
        postCommitHook.runCaptured();
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

        void clear() {
            action.set(null);
        }
    }
}
