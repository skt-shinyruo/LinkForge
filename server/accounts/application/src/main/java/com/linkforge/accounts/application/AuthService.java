package com.linkforge.accounts.application;

import com.linkforge.accounts.application.port.AccountStatusCache;
import com.linkforge.accounts.application.port.AccountsTenantStore;
import com.linkforge.accounts.application.port.AccountsPasswordHasher;
import com.linkforge.accounts.application.port.AccountsTokenIssuer;
import com.linkforge.accounts.application.port.AccountsUserRoleStore;
import com.linkforge.accounts.application.port.AccountsUserStore;
import com.linkforge.accounts.domain.AccountsConstants;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.security.StandardRoles;
import org.springframework.dao.DataIntegrityViolationException;
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

    private final SnowflakeIdGenerator idGenerator;
    private final AccountsTenantStore tenantStore;
    private final AccountsUserStore userStore;
    private final AccountsUserRoleStore userRoleStore;
    private final AccountsPasswordHasher passwordHasher;
    private final AccountsTokenIssuer tokenIssuer;
    private final AccountStatusCache statusCache;

    public AuthService(
            SnowflakeIdGenerator idGenerator,
            AccountsTenantStore tenantStore,
            AccountsUserStore userStore,
            AccountsUserRoleStore userRoleStore,
            AccountsPasswordHasher passwordHasher,
            AccountsTokenIssuer tokenIssuer,
            AccountStatusCache statusCache
    ) {
        this.idGenerator = idGenerator;
        this.tenantStore = tenantStore;
        this.userStore = userStore;
        this.userRoleStore = userRoleStore;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.statusCache = statusCache;
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
    public AuthResult login(String email, String rawPassword) {
        AccountsUserStore.UserData user = userStore.findFirstByEmail(email);
        if (user == null) {
            throw new BusinessException(AccountsErrorCode.INVALID_CREDENTIALS);
        }

        AccountsTenantStore.TenantData tenant = tenantStore.findById(user.tenantId());
        if (tenant == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "租户不存在");
        }

        if (!AccountsConstants.STATUS_ACTIVE.equals(tenant.status())) {
            throw new BusinessException(AccountsErrorCode.TENANT_DISABLED);
        }
        if (!AccountsConstants.STATUS_ACTIVE.equals(user.status())) {
            throw new BusinessException(AccountsErrorCode.USER_DISABLED);
        }
        if (!passwordHasher.matches(rawPassword, user.passwordHash())) {
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
     * <p>无效 ID 或已不存在的用户按幂等成功处理。版本写入后在同一事务内尽力驱逐状态缓存；驱逐
     * 失败或提交窗口内的并发请求仍可能保留旧快照，最迟由状态缓存的短 TTL 重新校准。</p>
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
        userStore.update(withIncrementedTokenVersion(user));
        statusCache.evictUserStatus(userId);
        statusCache.evictTenantStatus(user.tenantId());  // 立即驱逐租户缓存，缩小一致性窗口
    }

    private static AccountsUserStore.UserData withIncrementedTokenVersion(AccountsUserStore.UserData user) {
        int tokenVersion = user.tokenVersion() == null ? 0 : user.tokenVersion();
        return new AccountsUserStore.UserData(
                user.id(),
                user.tenantId(),
                user.email(),
                user.passwordHash(),
                user.status(),
                tokenVersion + 1,
                user.createdAt(),
                user.updatedAt()
        );
    }

}
