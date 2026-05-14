package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountStatusCache;
import com.linkforge.accounts.application.port.AccountsTenantStore;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.security.AccountStatusVerifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class AccountStatusService implements AccountStatusVerifier {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final int SKIP_TOKEN_VERSION_CHECK = Integer.MIN_VALUE;

    private final AccountsTenantStore tenantStore;
    private final AccountsUserStore userStore;
    private final AccountStatusCache statusCache;

    public AccountStatusService(AccountsTenantStore tenantStore, AccountsUserStore userStore, AccountStatusCache statusCache) {
        this.tenantStore = tenantStore;
        this.userStore = userStore;
        this.statusCache = statusCache;
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public void requireActiveUserAndTenant(long userId, long tenantId) {
        requireActiveUserAndTenant(userId, tenantId, SKIP_TOKEN_VERSION_CHECK);
    }

    @Transactional(readOnly = true)
    public void requireActiveUserAndTenant(long userId, long tenantId, int tokenVersion) {
        if (userId <= 0 || tenantId <= 0) {
            throw unauthorized();
        }

        requireActiveTenant(tenantId);

        AccountStatusCache.UserAuthState cached = statusCache.readUserAuthState(userId);
        if (cached != null) {
            validateUserAuthState(cached, tenantId, tokenVersion);
            return;
        }

        AccountsUserStore.UserData user = userStore.findById(userId);
        if (user == null || user.id() == null) {
            throw unauthorized();
        }
        if (user.tenantId() == null || user.tenantId() != tenantId) {
            throw unauthorized();
        }
        String status = normalizeUserStatus(user.status());
        int currentTokenVersion = user.tokenVersion() == null ? 0 : user.tokenVersion();
        statusCache.writeUserAuthState(userId, tenantId, status, currentTokenVersion, CACHE_TTL);

        if (!AccountsConstants.STATUS_ACTIVE.equals(status)) {
            throw new BusinessException(AccountsErrorCode.USER_DISABLED);
        }
        if (tokenVersion != SKIP_TOKEN_VERSION_CHECK && currentTokenVersion != tokenVersion) {
            throw unauthorized();
        }
    }

    private void validateUserAuthState(AccountStatusCache.UserAuthState authState, long tenantId, int tokenVersion) {
        if (authState.tenantId() != tenantId) {
            throw unauthorized();
        }
        if (!AccountsConstants.STATUS_ACTIVE.equals(authState.status())) {
            throw new BusinessException(AccountsErrorCode.USER_DISABLED);
        }
        if (tokenVersion != SKIP_TOKEN_VERSION_CHECK && authState.tokenVersion() != tokenVersion) {
            throw unauthorized();
        }
    }

    private static String normalizeUserStatus(String status) {
        return AccountsConstants.STATUS_ACTIVE.equals(status)
                ? AccountsConstants.STATUS_ACTIVE
                : AccountsConstants.STATUS_DISABLED;
    }

    private static BusinessException unauthorized() {
        return new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
