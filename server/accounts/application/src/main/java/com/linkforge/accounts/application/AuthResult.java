package com.linkforge.accounts.application;

import com.linkforge.foundation.security.AuthPrincipal;

/**
 * 登录或注册成功的令牌及同源主体快照；两者的用户、租户、角色和版本声明必须一致。
 *
 * <p>{@code token} 是完整认证凭据，只能交付给认证接口，禁止写入业务日志或普通持久化字段。</p>
 */
public record AuthResult(String token, AuthPrincipal principal) {
}
