package com.linkforge.foundation.security;

/**
 * 认证令牌、方法安全和业务授权共用的标准角色字符串。
 *
 * <p>常量不隐含授权能力；每个端点或应用服务仍须按资源和操作定义权限矩阵。Spring Security 使用时会在
 * 过滤器中添加 {@code ROLE_} 前缀，常量本身不带此前缀。</p>
 */
public final class StandardRoles {

    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    public static final String USER = "USER";
    public static final String OPENAPI = "OPENAPI";

    private StandardRoles() {
    }
}
