package com.linkforge.foundation.runtime.security;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.foundation.security.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthContextTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requirePrincipal_shouldReturnAuthenticatedLinkForgePrincipal() {
        AuthPrincipal principal = new AuthPrincipal(9L, 1L, "admin@example.com", Set.of("TENANT_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal,
                "N/A",
                Set.of()
        ));

        assertThat(AuthContext.requirePrincipal()).isSameAs(principal);
    }

    @Test
    void requirePrincipal_shouldRejectUnauthenticatedTokenEvenWhenPrincipalTypeMatches() {
        AuthPrincipal principal = new AuthPrincipal(9L, 1L, "admin@example.com", Set.of("TENANT_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "N/A")
        );

        assertThatThrownBy(AuthContext::requirePrincipal)
                .isInstanceOf(BusinessException.class);
    }
}
