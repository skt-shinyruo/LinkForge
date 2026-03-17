package com.linkforge.accounts.application;

import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.accounts.infrastructure.persistence.entity.TenantEntity;
import com.linkforge.accounts.infrastructure.persistence.entity.UserEntity;
import com.linkforge.accounts.infrastructure.persistence.mapper.TenantMapper;
import com.linkforge.accounts.infrastructure.persistence.mapper.UserMapper;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Account status checks for auth flows.
 *
 * <p>Motivation: tenant/user status is validated at login time today, but once a JWT/API key is issued,
 * status changes (disable tenant / disable user) must take effect for subsequent requests.</p>
 *
 * <p>Implementation: best-effort Redis cache with short TTL to avoid per-request DB hits. If Redis is
 * unavailable, falls back to DB checks.</p>
 */
@Service
public class AccountStatusService {

    private static final Logger log = LoggerFactory.getLogger(AccountStatusService.class);

    private static final String TENANT_STATUS_KEY_PREFIX = "auth:tenant_status:";
    private static final String USER_STATUS_KEY_PREFIX = "auth:user_status:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redis;

    public AccountStatusService(TenantMapper tenantMapper, UserMapper userMapper, StringRedisTemplate redis) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
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

        TenantEntity tenant = tenantMapper.findById(tenantId);
        if (tenant == null || tenant.getId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String status = tenant.getStatus();
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

        UserEntity user = userMapper.findById(userId);
        if (user == null || user.getId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        // Defense-in-depth: ensure token tenantId still matches DB record.
        if (user.getTenantId() == null || user.getTenantId() != tenantId) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String status = user.getStatus();
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

