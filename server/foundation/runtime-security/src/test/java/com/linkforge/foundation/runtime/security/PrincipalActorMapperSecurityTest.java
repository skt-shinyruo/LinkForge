package com.linkforge.foundation.runtime.security;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.foundation.context.ApiKeyActor;
import com.linkforge.foundation.security.ApiKeyAuthenticationDetails;
import com.linkforge.foundation.security.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrincipalActorMapperSecurityTest {

    private final PrincipalActorMapper mapper = new PrincipalActorMapper();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireApiKey_shouldBuildActorFromAuthenticationDetails() {
        AuthPrincipal principal = new AuthPrincipal(0L, 1L, null, Set.of("OPENAPI"));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                "N/A",
                Set.of()
        );
        authentication.setDetails(new ApiKeyAuthenticationDetails(123L, 2001L));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        ApiKeyActor actor = mapper.requireApiKey(principal);

        assertThat(actor.tenantId()).isEqualTo(1L);
        assertThat(actor.apiKeyId()).isEqualTo(123L);
        assertThat(actor.applicationId()).isEqualTo(2001L);
    }

    @Test
    void requireApiKey_shouldRejectMissingAuthenticationDetails() {
        AuthPrincipal principal = new AuthPrincipal(0L, 1L, null, Set.of("OPENAPI"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal,
                "N/A",
                Set.of()
        ));

        assertThatThrownBy(() -> mapper.requireApiKey(principal))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void requireApiKey_shouldRejectUnauthenticatedContext() {
        AuthPrincipal principal = new AuthPrincipal(0L, 1L, null, Set.of("OPENAPI"));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, "N/A");
        authentication.setDetails(new ApiKeyAuthenticationDetails(123L, 2001L));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(() -> mapper.requireApiKey(principal))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void requireApiKey_shouldRejectMismatchedContextPrincipal() {
        AuthPrincipal contextPrincipal = new AuthPrincipal(0L, 1L, null, Set.of("OPENAPI"));
        AuthPrincipal argumentPrincipal = new AuthPrincipal(0L, 2L, null, Set.of("OPENAPI"));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                contextPrincipal,
                "N/A",
                Set.of()
        );
        authentication.setDetails(new ApiKeyAuthenticationDetails(123L, 2001L));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(() -> mapper.requireApiKey(argumentPrincipal))
                .isInstanceOf(BusinessException.class);
    }
}
