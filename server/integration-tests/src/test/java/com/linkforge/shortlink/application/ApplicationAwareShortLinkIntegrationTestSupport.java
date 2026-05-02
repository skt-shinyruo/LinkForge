package com.linkforge.shortlink.application;

import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.security.StandardRoles;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;

import java.time.Duration;
import java.util.List;
import java.util.Set;

abstract class ApplicationAwareShortLinkIntegrationTestSupport {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("linkforge")
            .withUsername("linkforge")
            .withPassword("linkforge");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.6.2-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1)
                    .withStartupTimeout(Duration.ofSeconds(120)))
            .withStartupAttempts(3);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("app.security.jwt.secret", () -> "test-secret-please-change-but-long-enough-32-bytes");
        registry.add("app.analytics.salt", () -> "test-analytics-salt");
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
