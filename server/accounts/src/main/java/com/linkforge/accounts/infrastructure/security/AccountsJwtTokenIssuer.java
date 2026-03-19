package com.linkforge.accounts.infrastructure.security;

import com.linkforge.accounts.application.port.AccountsTokenIssuer;
import org.springframework.stereotype.Component;

import java.util.Set;

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
