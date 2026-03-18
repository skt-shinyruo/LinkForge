package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountsTenantStore;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    private static final Logger log = LoggerFactory.getLogger(AccountStatusService.class);

    private static final String TENANT_STATUS_KEY_PREFIX = "auth:tenant_status:";
    private static final String USER_STATUS_KEY_PREFIX = "auth:user_status:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final AccountsTenantStore tenantStore;
    private final AccountsUserStore userStore;
    private final StringRedisTemplate redis;

    public AccountStatusService(AccountsTenantStore tenantStore, AccountsUserStore userStore, StringRedisTemplate redis) {
        this.tenantStore = tenantStore;
        this.userStore = userStore;
        this.redis = redis;
    }

    public void requireActiveTenant(long tenantId) {
        if (tenantId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String cached = readStatusQuietly(tenantStatusKey(tenantId));
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
            writeStatusQuietly(tenantStatusKey(tenantId), AccountsConstants.STATUS_DISABLED);
            throw new BusinessException(AccountsErrorCode.TENANT_DISABLED);
        }
        writeStatusQuietly(tenantStatusKey(tenantId), AccountsConstants.STATUS_ACTIVE);
    }

    public void requireActiveUserAndTenant(long userId, long tenantId) {
        if (userId <= 0 || tenantId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        requireActiveTenant(tenantId);

        String cached = readStatusQuietly(userStatusKey(userId));
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
            writeStatusQuietly(userStatusKey(userId), AccountsConstants.STATUS_DISABLED);
            throw new BusinessException(AccountsErrorCode.USER_DISABLED);
        }
        writeStatusQuietly(userStatusKey(userId), AccountsConstants.STATUS_ACTIVE);
    }

    private String readStatusQuietly(String key) {
        if (redis == null || key == null || key.isBlank()) {
            return null;
        }
        try {
            String v = redis.opsForValue().get(key);
            if (v == null || v.isBlank()) {
                return null;
            }
            return v.trim();
        } catch (Exception e) {
            log.debug("status cache read failed: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    private void writeStatusQuietly(String key, String status) {
        if (redis == null || key == null || key.isBlank()) {
            return;
        }
        if (status == null || status.isBlank()) {
            return;
        }
        try {
            redis.opsForValue().set(key, status, CACHE_TTL);
        } catch (Exception e) {
            log.debug("status cache write failed: key={}, err={}", key, e.getMessage());
        }
    }

    private static String tenantStatusKey(long tenantId) {
        return TENANT_STATUS_KEY_PREFIX + tenantId;
    }

    private static String userStatusKey(long userId) {
        return USER_STATUS_KEY_PREFIX + userId;
    }
}
