package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountStatusCache;
import com.linkforge.accounts.application.port.AccountsTenantStore;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Account status checks for authentication flows.
 *
 * <p>Tenant/user status is validated at login time, but issued JWT/API keys can outlive status
 * changes. This service enforces status checks on subsequent requests.</p>
 *
 * <p>Implementation uses best-effort Redis caching with short TTL to reduce DB pressure. When Redis
 * is unavailable, checks fall back to the persistence store.</p>
 */
@Service
public class AccountStatusService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final AccountsTenantStore tenantStore;
    private final AccountsUserStore userStore;
    private final AccountStatusCache statusCache;

    public AccountStatusService(AccountsTenantStore tenantStore, AccountsUserStore userStore, AccountStatusCache statusCache) {
        this.tenantStore = tenantStore;
        this.userStore = userStore;
        this.statusCache = statusCache;
    }

    public void requireActiveTenant(long tenantId) {
        if (tenantId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String cached = statusCache.readTenantStatus(tenantId);
        if (cached != null) {
            if (AccountsConstants.STATUS_ACTIVE.equals(cached)) {
                return;
            }
            if (AccountsConstants.STATUS_DISABLED.equals(cached)) {
                throw new BusinessException(AccountsErrorCode.TENANT_DISABLED);
            }
        }

        AccountsTenantStore.TenantData tenant = tenantStore.findById(tenantId);
        if (tenant == null || tenant.id() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String status = tenant.status();
        if (!AccountsConstants.STATUS_ACTIVE.equals(status)) {
            statusCache.writeTenantStatus(tenantId, AccountsConstants.STATUS_DISABLED, CACHE_TTL);
            throw new BusinessException(AccountsErrorCode.TENANT_DISABLED);
        }
        statusCache.writeTenantStatus(tenantId, AccountsConstants.STATUS_ACTIVE, CACHE_TTL);
    }

    public void requireActiveUserAndTenant(long userId, long tenantId) {
        if (userId <= 0 || tenantId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        requireActiveTenant(tenantId);

        String cached = statusCache.readUserStatus(userId);
        if (cached != null) {
            if (AccountsConstants.STATUS_ACTIVE.equals(cached)) {
                return;
            }
            if (AccountsConstants.STATUS_DISABLED.equals(cached)) {
                throw new BusinessException(AccountsErrorCode.USER_DISABLED);
            }
        }

        AccountsUserStore.UserData user = userStore.findById(userId);
        if (user == null || user.id() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (user.tenantId() == null || user.tenantId() != tenantId) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String status = user.status();
        if (!AccountsConstants.STATUS_ACTIVE.equals(status)) {
            statusCache.writeUserStatus(userId, AccountsConstants.STATUS_DISABLED, CACHE_TTL);
            throw new BusinessException(AccountsErrorCode.USER_DISABLED);
        }
        statusCache.writeUserStatus(userId, AccountsConstants.STATUS_ACTIVE, CACHE_TTL);
    }
}
