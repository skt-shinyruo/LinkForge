package com.linkforge.foundation.security;

import java.util.Set;

/**
 * 认证过滤链交给 HTTP 适配层的用户主体快照。
 *
 * <p>{@code tokenVersion} 来自 JWT，并在每次受保护请求中与 Accounts 当前版本比较；角色只用于
 * 当前请求的方法级授权。API Key 的 application scope 存在 Authentication details 中，不使用本类型。</p>
 */
public class AuthPrincipal {

    private final long userId;
    private final long tenantId;
    private final String email;
    private final Set<String> roles;
    private final int tokenVersion;

    /** 使用兼容默认 tokenVersion {@code 0} 构造主体，适用于不含版本 claim 的历史 JWT。 */
    public AuthPrincipal(long userId, long tenantId, String email, Set<String> roles) {
        this(userId, tenantId, email, roles, 0);
    }

    /**
     * 构造不可变的认证快照。
     *
     * <p>调用方负责在令牌解析时验证 claim；本类型不校验 ID 正负或复制 roles，因而仅应由受信认证链路创建。
     * {@code tokenVersion} 是账户状态验证的输入，不是角色版本。</p>
     */
    public AuthPrincipal(long userId, long tenantId, String email, Set<String> roles, int tokenVersion) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.email = email;
        this.roles = roles;
        this.tokenVersion = tokenVersion;
    }

    public long getUserId() {
        return userId;
    }

    public long getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

}
