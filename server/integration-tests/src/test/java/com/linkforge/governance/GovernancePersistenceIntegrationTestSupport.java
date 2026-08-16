package com.linkforge.governance;

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

abstract class GovernancePersistenceIntegrationTestSupport extends SharedIntegrationTestSupport {

    @DynamicPropertySource
    static void governanceProperties(DynamicPropertyRegistry registry) {
        registerDisabledAnalytics(registry);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    protected static void authenticateAsTenantAdmin(long tenantId, long userId, String email) {
        setAuthentication(userId, tenantId, email, Set.of(StandardRoles.TENANT_ADMIN));
    }

    protected static void authenticateAsPlatformAdmin(long tenantId, long userId, String email) {
        setAuthentication(userId, tenantId, email, Set.of(StandardRoles.PLATFORM_ADMIN));
    }

    private static void setAuthentication(long userId, long tenantId, String email, Set<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(userId, tenantId, email, roles);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "N/A", List.of())
        );
    }
}
