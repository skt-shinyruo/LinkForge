package com.linkforge.accounts.infrastructure.security;

import com.linkforge.accounts.application.port.AccountsTokenIssuer;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 将 Accounts 应用层的令牌签发端口适配到统一的 {@link JwtService}。
 *
 * <p>该适配器不复制 claims、TTL 或签名规则，确保登录与底层 JWT 校验共享同一份安全配置。</p>
 */
@Component
public class AccountsJwtTokenIssuer implements AccountsTokenIssuer {

    private final JwtService jwtService;

    public AccountsJwtTokenIssuer(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public String issueToken(long userId, long tenantId, String email, Set<String> roles, int tokenVersion) {
        return jwtService.issueToken(userId, tenantId, email, roles, tokenVersion);
    }
}
