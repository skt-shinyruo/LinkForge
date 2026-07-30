package com.linkforge.foundation.runtime.security;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.security.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 为 HTTP 适配层读取已经通过 Spring Security 认证的 LinkForge 主体。
 *
 * <p>这里同时检查 {@link Authentication#isAuthenticated()} 和主体类型，避免把测试桩、匿名 token
 * 或仅完成构造但尚未认证的 token 当成可信业务参与者。</p>
 */
public final class AuthContext {

    private AuthContext() {
    }

    /**
     * 返回当前认证主体。
     *
     * @throws BusinessException 当前请求没有完成认证，或认证主体不是 {@link AuthPrincipal} 时抛出
     */
    public static AuthPrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }
}
