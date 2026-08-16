package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountStatusCache;
import com.linkforge.accounts.application.port.AccountsTenantStore;
import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.accounts.application.port.AccountsTokenIssuer;
import com.linkforge.accounts.application.port.AccountsUserRoleStore;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.security.StandardRoles;
import com.linkforge.foundation.tx.PostCommitHookPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 账号注册、密码登录与会话失效的应用服务。
 *
 * <p>注册在一个事务内创建租户、首个用户及 {@code TENANT_ADMIN} 角色；邮箱预查询只用于尽早
 * 返回业务错误，数据库唯一约束仍是并发注册时的最终裁决。密码只经由
 * {@link AccountsPasswordHasher} 处理，应用层不会持久化或回传明文密码。</p>
 *
 * <p>JWT 携带用户当前的 {@code tokenVersion}。注销通过递增该版本使既有令牌在后续请求的
 * 账号状态校验中失效，而不是维护服务端令牌黑名单。注册开关、Cookie 写入和 HTTP 参数校验
 * 属于接口层职责，不由本服务处理。</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String DUMMY_PASSWORD = "linkforge-dummy-password-verification";

    private final SnowflakeIdGenerator idGenerator;
    private final AccountsTenantStore tenantStore;
    private final AccountsUserStore userStore;
    private final AccountsUserRoleStore userRoleStore;
    private final AccountsPasswordHasher passwordHasher;
    private final AccountsTokenIssuer tokenIssuer;
    private final AccountStatusCache statusCache;
    private final PostCommitHookPort postCommitHookPort;
    private final String dummyPasswordHash;

    public AuthService(
            SnowflakeIdGenerator idGenerator,
            AccountsTenantStore tenantStore,
            AccountsUserStore userStore,
            AccountsUserRoleStore userRoleStore,
            AccountsPasswordHasher passwordHasher,
            AccountsTokenIssuer tokenIssuer,
            AccountStatusCache statusCache,
            PostCommitHookPort postCommitHookPort
    ) {
        this.idGenerator = idGenerator;
        this.tenantStore = tenantStore;
        this.userStore = userStore;
        this.userRoleStore = userRoleStore;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.statusCache = statusCache;
        this.postCommitHookPort = postCommitHookPort;
        this.dummyPasswordHash = passwordHasher.encode(DUMMY_PASSWORD);
    }

    /**
     * 原子创建新租户及其首个管理员并签发初始令牌。
     *
     * @throws BusinessException 邮箱已存在，或底层唯一约束在并发写入时判定冲突
     */
    @Transactional
    public AuthResult register(String tenantName, String email, String rawPassword) {
        if (userStore.findFirstByEmail(email) != null) {
            throw new BusinessException(AccountsErrorCode.EMAIL_ALREADY_EXISTS);
        }

        long tenantId = idGenerator.nextId();
        tenantStore.insert(new AccountsTenantStore.TenantData(
                tenantId,
                tenantName,
                AccountsConstants.STATUS_ACTIVE,
                null,
                null
        ));

        long userId = idGenerator.nextId();
        AccountsUserStore.UserData user = new AccountsUserStore.UserData(
                userId,
                tenantId,
                email,
                passwordHasher.encode(rawPassword),
                AccountsConstants.STATUS_ACTIVE,
                0,
                null,
                null
        );
        try {
            userStore.insert(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AccountsErrorCode.EMAIL_ALREADY_EXISTS);
        }

        userRoleStore.insert(new AccountsUserRoleStore.UserRoleData(userId, StandardRoles.TENANT_ADMIN));

        Set<String> roles = Set.of(StandardRoles.TENANT_ADMIN);
        String token = tokenIssuer.issueToken(userId, tenantId, email, roles, 0);
        return new AuthResult(token, new AuthPrincipal(userId, tenantId, email, roles, 0));
    }

    /**
     * 使用邮箱和密码登录。
     *
     * <p>只有租户与用户均为 {@code active} 且密码匹配时才签发令牌。为避免暴露账号是否存在，
     * 用户不存在与密码错误统一返回无效凭据；存量用户没有角色记录时按普通 {@code USER} 处理。</p>
     */
    @Transactional(readOnly = true)
    public AuthResult login(String email, String rawPassword) {
        AccountsUserStore.UserData user = userStore.findFirstByEmail(email);
        String passwordHash = user == null ? dummyPasswordHash : user.passwordHash();
        boolean passwordMatches = passwordHasher.matches(rawPassword, passwordHash);
        if (user == null) {
            throw new BusinessException(AccountsErrorCode.INVALID_CREDENTIALS);
        }

        AccountsTenantStore.TenantData tenant = tenantStore.findById(user.tenantId());
        if (tenant == null) {
            throw new BusinessException(AccountsErrorCode.INVALID_CREDENTIALS);
        }

        if (!AccountsConstants.STATUS_ACTIVE.equals(tenant.status())) {
            throw new BusinessException(AccountsErrorCode.INVALID_CREDENTIALS);
        }
        if (!AccountsConstants.STATUS_ACTIVE.equals(user.status())) {
            throw new BusinessException(AccountsErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordMatches) {
            throw new BusinessException(AccountsErrorCode.INVALID_CREDENTIALS);
        }

        Set<String> roles = userRoleStore.findAllByUserId(user.id()).stream()
                .filter(r -> r != null)
                .map(AccountsUserRoleStore.UserRoleData::roleCode)
                .collect(Collectors.toUnmodifiableSet());

        if (roles.isEmpty()) {
            roles = Set.of(StandardRoles.USER);
        }

        int tokenVersion = user.tokenVersion() == null ? 0 : user.tokenVersion();
        String token = tokenIssuer.issueToken(user.id(), user.tenantId(), user.email(), roles, tokenVersion);
        return new AuthResult(token, new AuthPrincipal(user.id(), user.tenantId(), user.email(), roles, tokenVersion));
    }

    /**
     * 使用户当前及更早版本的 JWT 失效。
     *
     * <p>无效 ID 或已不存在的用户按幂等成功处理。版本写入成功后注册 after-commit 缓存驱逐；
     * 回滚不会驱逐，提交后的驱逐失败会记录告警且不改变已提交的撤销事实。</p>
     */
    @Transactional
    public void logout(long userId) {
        if (userId <= 0) {
            return;
        }
        AccountsUserStore.UserData user = userStore.findById(userId);
        if (user == null) {
            return;
        }
        if (!userStore.incrementTokenVersion(userId)) {
            return;
        }
        evictAccountStatusAfterCommit(userId, user.tenantId());
    }

    private void evictAccountStatusAfterCommit(long userId, Long tenantId) {
        postCommitHookPort.run(() -> {
            try {
                boolean userEvicted = statusCache.evictUserStatus(userId);
                boolean tenantEvicted = true;
                if (tenantId != null && tenantId > 0) {
                    tenantEvicted = statusCache.evictTenantStatus(tenantId);
                }
                if (!userEvicted || !tenantEvicted) {
                    log.warn("account status cache eviction incomplete after commit: userId={}, tenantId={}, userEvicted={}, tenantEvicted={}",
                            userId, tenantId, userEvicted, tenantEvicted);
                }
            } catch (RuntimeException ex) {
                log.warn("account status cache eviction failed after commit: userId={}, tenantId={}, err={}",
                        userId, tenantId, ex.getMessage());
            }
        });
    }

}
