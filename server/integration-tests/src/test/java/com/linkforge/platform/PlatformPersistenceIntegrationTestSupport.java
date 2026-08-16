package com.linkforge.platform;

import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.testsupport.SharedIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

abstract class PlatformPersistenceIntegrationTestSupport extends SharedIntegrationTestSupport {

    @DynamicPropertySource
    static void platformProperties(DynamicPropertyRegistry registry) {
        registerDisabledAnalytics(registry);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    protected static void authenticateAsTenantAdmin(long tenantId) {
        AuthPrincipal principal = new AuthPrincipal(101L, tenantId, "tenant-admin@example.com", java.util.Set.of("tenant_admin"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "N/A", List.of())
        );
    }

    protected static UserActor tenantAdminActor(long tenantId) {
        return new UserActor(tenantId, 101L, "tenant-admin@example.com", java.util.Set.of("tenant_admin"));
    }
}
