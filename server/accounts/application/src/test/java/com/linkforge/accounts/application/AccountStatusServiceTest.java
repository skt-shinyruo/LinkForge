package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountStatusCache;
import com.linkforge.accounts.application.port.AccountsTenantStore;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountStatusServiceTest {

    @Test
    void accountStatusVerifierEntryPoints_shouldRunInTransactionSoReadwriteSplittingReadsPrimary() throws Exception {
        Method tenantOnly = AccountStatusService.class.getMethod("requireActiveTenant", long.class);
        Method userAndTenant = AccountStatusService.class.getMethod("requireActiveUserAndTenant", long.class, long.class);
        Method userTenantAndTokenVersion = AccountStatusService.class.getMethod(
                "requireActiveUserAndTenant",
                long.class,
                long.class,
                int.class
        );

        assertThat(tenantOnly.getAnnotation(Transactional.class)).isNotNull();
        assertThat(userAndTenant.getAnnotation(Transactional.class)).isNotNull();
        assertThat(userTenantAndTokenVersion.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void requireActiveUserAndTenant_shouldAcceptMatchingCachedAuthState_withoutFallingBackToStore() {
        AccountsTenantStore tenantStore = mock(AccountsTenantStore.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);
        AccountStatusService service = new AccountStatusService(tenantStore, userStore, statusCache);

        when(statusCache.readTenantStatus(200L)).thenReturn(AccountsConstants.STATUS_ACTIVE);
        when(statusCache.readUserAuthState(100L))
                .thenReturn(new AccountStatusCache.UserAuthState(200L, AccountsConstants.STATUS_ACTIVE, 7));

        service.requireActiveUserAndTenant(100L, 200L, 7);

        verifyNoInteractions(tenantStore);
        verifyNoInteractions(userStore);
    }

    @Test
    void requireActiveUserAndTenant_shouldRejectCachedTokenVersionMismatch_asUnauthorized() {
        AccountsTenantStore tenantStore = mock(AccountsTenantStore.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);
        AccountStatusService service = new AccountStatusService(tenantStore, userStore, statusCache);

        when(statusCache.readTenantStatus(200L)).thenReturn(AccountsConstants.STATUS_ACTIVE);
        when(statusCache.readUserAuthState(100L))
                .thenReturn(new AccountStatusCache.UserAuthState(200L, AccountsConstants.STATUS_ACTIVE, 7));

        assertThatThrownBy(() -> service.requireActiveUserAndTenant(100L, 200L, 6))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(tenantStore);
        verifyNoInteractions(userStore);
    }

    @Test
    void requireActiveUserAndTenant_shouldCacheResolvedUserAuthState_afterStoreFallback() {
        AccountsTenantStore tenantStore = mock(AccountsTenantStore.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);
        AccountStatusService service = new AccountStatusService(tenantStore, userStore, statusCache);

        when(statusCache.readTenantStatus(200L)).thenReturn(null);
        when(statusCache.readUserAuthState(100L)).thenReturn(null);
        when(statusCache.readTenantGeneration(200L)).thenReturn(3L);
        when(statusCache.readUserGeneration(100L)).thenReturn(4L);
        when(tenantStore.findById(200L))
                .thenReturn(new AccountsTenantStore.TenantData(200L, "tenant", AccountsConstants.STATUS_ACTIVE, null, null));
        when(userStore.findById(100L))
                .thenReturn(new AccountsUserStore.UserData(
                        100L,
                        200L,
                        "member@example.com",
                        "hash",
                        AccountsConstants.STATUS_ACTIVE,
                        9,
                        null,
                        null
                ));

        service.requireActiveUserAndTenant(100L, 200L, 9);

        verify(statusCache).writeTenantStatusIfGenerationMatches(
                200L, 3L, AccountsConstants.STATUS_ACTIVE, Duration.ofSeconds(30)
        );
        verify(statusCache).writeUserAuthStateIfGenerationMatches(
                100L, 4L, 200L, AccountsConstants.STATUS_ACTIVE, 9, Duration.ofSeconds(30)
        );
        verify(tenantStore).findById(200L);
        verify(userStore).findById(100L);

        var ordered = inOrder(statusCache, tenantStore, userStore);
        ordered.verify(statusCache).readUserGeneration(100L);
        ordered.verify(tenantStore).findById(200L);
        ordered.verify(userStore).findById(100L);
        ordered.verify(statusCache).writeUserAuthStateIfGenerationMatches(
                100L, 4L, 200L, AccountsConstants.STATUS_ACTIVE, 9, Duration.ofSeconds(30)
        );
    }

    @Test
    void requireActiveUserAndTenant_shouldSkipPopulationWhenRedisCannotSupplyGeneration() {
        AccountsTenantStore tenantStore = mock(AccountsTenantStore.class);
        AccountsUserStore userStore = mock(AccountsUserStore.class);
        AccountStatusCache statusCache = mock(AccountStatusCache.class);
        AccountStatusService service = new AccountStatusService(tenantStore, userStore, statusCache);

        when(statusCache.readTenantGeneration(200L)).thenReturn(null);
        when(statusCache.readUserGeneration(100L)).thenReturn(null);
        when(tenantStore.findById(200L))
                .thenReturn(new AccountsTenantStore.TenantData(200L, "tenant", AccountsConstants.STATUS_ACTIVE, null, null));
        when(userStore.findById(100L))
                .thenReturn(new AccountsUserStore.UserData(
                        100L, 200L, "member@example.com", "hash", AccountsConstants.STATUS_ACTIVE, 9, null, null
                ));

        service.requireActiveUserAndTenant(100L, 200L, 9);

        verify(statusCache, never()).writeTenantStatusIfGenerationMatches(
                anyLong(), anyLong(), any(String.class), any(Duration.class)
        );
        verify(statusCache, never()).writeUserAuthStateIfGenerationMatches(
                anyLong(), anyLong(), anyLong(), any(String.class), anyInt(), any(Duration.class)
        );
    }
}
