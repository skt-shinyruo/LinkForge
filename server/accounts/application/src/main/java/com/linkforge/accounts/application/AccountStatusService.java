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
 * 认证请求的租户、用户状态与令牌版本校验器。
 *
 * <p>登录时的状态检查不能覆盖 JWT 或 API Key 的整个有效期，因此安全过滤器会在后续请求中调用
 * 本服务。租户先于用户校验；用户还必须属于令牌中的租户，提供 {@code tokenVersion} 时还必须与
 * 当前缓存快照或持久化快照中的版本一致。</p>
 *
 * <p>状态缓存使用 30 秒 TTL，并以 {@code null} 同时表示未命中、无效缓存值或缓存不可用，随后安全
 * 地回源数据库。未知用户状态统一收敛为 disabled；不存在的租户/用户、归属不符和版本不符统一返回
 * unauthorized，避免泄漏账号信息。持久化记录是权威来源，但命中的状态快照会在 30 秒 TTL 内直接
 * 参与认证判断，因此禁用、启用和令牌撤销允许存在有界的缓存陈旧窗口。</p>
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

    /**
     * 要求租户存在且处于启用状态。
     *
     * <p>缓存只识别 {@code active}/{@code disabled}；其他值按未命中处理。持久化中的非 active 状态
     * 都按 disabled 缓存和拒绝。</p>
     */
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

    /**
     * 校验用户、租户和归属关系，但不校验 JWT 版本。
     *
     * <p>该重载用于没有用户令牌版本语义的可信调用路径。</p>
     */
    @Transactional(readOnly = true)
    public void requireActiveUserAndTenant(long userId, long tenantId) {
        requireActiveUserAndTenant(userId, tenantId, SKIP_TOKEN_VERSION_CHECK);
    }

    /**
     * 校验用户和租户均启用、用户属于租户，且当前版本与令牌版本一致。
     *
     * <p>版本不匹配表示令牌已因注销或密码重置而失效，统一返回 unauthorized。</p>
     */
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
