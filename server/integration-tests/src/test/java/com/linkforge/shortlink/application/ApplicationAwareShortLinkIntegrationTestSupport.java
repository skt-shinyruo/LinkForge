package com.linkforge.shortlink.application;

import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.security.StandardRoles;
import com.linkforge.testsupport.SharedIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Set;

abstract class ApplicationAwareShortLinkIntegrationTestSupport extends SharedIntegrationTestSupport {

    @DynamicPropertySource
    static void shortLinkProperties(DynamicPropertyRegistry registry) {
        registry.add("app.analytics.dimensions.enabled", () -> "false");
        registry.add("app.analytics.events.enabled", () -> "false");
        registry.add("app.analytics.events.sample-rate", () -> "1");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    protected static void authenticateAsTenantAdmin(long tenantId, long userId, String email) {
        AuthPrincipal principal = new AuthPrincipal(userId, tenantId, email, Set.of(StandardRoles.TENANT_ADMIN));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "N/A", List.of())
        );
    }
}
