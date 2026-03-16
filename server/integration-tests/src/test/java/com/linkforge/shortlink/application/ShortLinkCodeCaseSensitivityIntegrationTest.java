package com.linkforge.shortlink.application;

import com.linkforge.LinkForgeApplication;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.redirect.infrastructure.projection.ShortLinkEventProjectorJob;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkQueryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.scheduling.enabled=false"
)
class ShortLinkCodeCaseSensitivityIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("linkforge")
            .withUsername("linkforge")
            .withPassword("linkforge")
            // Align with docker-compose default: case-insensitive server collation.
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2.4-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1)
                    .withStartupTimeout(Duration.ofSeconds(120)))
            .withStartupAttempts(3);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);

        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // Test environment fixed values to avoid startup failure.
        r.add("app.security.jwt.secret", () -> "test-secret-please-change-but-long-enough-32-bytes");
        r.add("app.analytics.salt", () -> "test-analytics-salt");

        // Keep tests stable (avoid scheduling side effects).
        r.add("app.analytics.dimensions.enabled", () -> "false");
        r.add("app.analytics.events.enabled", () -> "false");
        r.add("app.analytics.events.sample-rate", () -> "1");
    }

    @Autowired
    ShortLinkService shortLinkService;

    @Autowired
    ShortLinkQueryMapper shortLinkQueryMapper;

    @Autowired
    ShortLinkEventProjectorJob redirectProjector;

    @Autowired
    StringRedisTemplate redis;

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 1L;

    @BeforeEach
    void setUpAuth() {
        AuthPrincipal principal = new AuthPrincipal(USER_ID, TENANT_ID, "admin@example.com", Set.of("tenant_admin"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "N/A", List.of())
        );
    }

    @AfterEach
    void tearDownAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customCode_shouldBeCaseSensitive_andProjectionShouldNotCollapse() {
        ShortLinkService.CreateLinkRequest req1 = new ShortLinkService.CreateLinkRequest(
                "https://example.com/a",
                null,
                null,
                null,
                "Abcdef",
                Set.of(),
                null,
                null,
                null,
                null,
                null
        );
        ShortLinkService.CreateLinkRequest req2 = new ShortLinkService.CreateLinkRequest(
                "https://example.com/b",
                null,
                null,
                null,
                "abcdef",
                Set.of(),
                null,
                null,
                null,
                null,
                null
        );

        ShortLinkService.LinkDto a = shortLinkService.create(TENANT_ID, USER_ID, req1);
        ShortLinkService.LinkDto b = shortLinkService.create(TENANT_ID, USER_ID, req2);

        assertThat(a.code()).isEqualTo("Abcdef");
        assertThat(b.code()).isEqualTo("abcdef");

        ShortLinkEntity linkA = shortLinkQueryMapper.findByCode("Abcdef");
        assertThat(linkA).isNotNull();
        assertThat(linkA.getCode()).isEqualTo("Abcdef");

        ShortLinkEntity linkB = shortLinkQueryMapper.findByCode("abcdef");
        assertThat(linkB).isNotNull();
        assertThat(linkB.getCode()).isEqualTo("abcdef");
        assertThat(linkA.getId()).isNotEqualTo(linkB.getId());

        redirectProjector.drain();
        assertThat(redis.opsForValue().get(key("Abcdef"))).isNotNull();
        assertThat(redis.opsForValue().get(key("abcdef"))).isNotNull();
    }

    private static String key(String code) {
        return "link:code:" + code;
    }
}
