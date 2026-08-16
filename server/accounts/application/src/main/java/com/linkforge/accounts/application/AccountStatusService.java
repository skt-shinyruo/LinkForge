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
     * unauthorized，避免泄漏账号信息。持久化记录是权威来源；回源前读取 generation，回填时仅在它
     * 未变化时写入，因此事务提交前读到、提交后才完成的旧快照不能越过提交后失效重新污染缓存。若
     * Redis 失效本身失败，既有命中快照仍可能在 30 秒 TTL 内陈旧，并通过 warning 暴露。</p>
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

        Long generation = statusCache.readTenantGeneration(tenantId);
        AccountsTenantStore.TenantData tenant = tenantStore.findById(tenantId);
        if (tenant == null || tenant.id() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String status = tenant.status();
        if (!AccountsConstants.STATUS_ACTIVE.equals(status)) {
            writeTenantStatus(tenantId, generation, AccountsConstants.STATUS_DISABLED);
            throw new BusinessException(AccountsErrorCode.TENANT_DISABLED);
        }
        writeTenantStatus(tenantId, generation, AccountsConstants.STATUS_ACTIVE);
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

        // 必须早于任何数据库读取，避免同一事务的旧快照携带提交后的新 generation 写回缓存。
        Long userGeneration = statusCache.readUserGeneration(userId);
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
        if (userGeneration != null) {
            statusCache.writeUserAuthStateIfGenerationMatches(
                    userId,
                    userGeneration,
                    tenantId,
                    status,
                    currentTokenVersion,
                    CACHE_TTL
            );
        }

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

    private void writeTenantStatus(long tenantId, Long generation, String status) {
        if (generation != null) {
            statusCache.writeTenantStatusIfGenerationMatches(tenantId, generation, status, CACHE_TTL);
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
