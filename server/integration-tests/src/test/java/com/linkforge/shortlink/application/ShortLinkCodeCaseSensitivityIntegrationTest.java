package com.linkforge.shortlink.application;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.redirect.application.RedirectService;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkQueryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.6.2-alpine")
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
    ShortLinkApplicationService shortLinkService;

    @Autowired
    ShortLinkQueryMapper shortLinkQueryMapper;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    RedirectService redirectService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 1L;

    @BeforeEach
    void setUpAuth() {
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, TENANT_ID);
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
    void customCode_shouldBeCaseSensitive_andReadThroughShouldNotCollapse() {
        CreateLinkRequest req1 = new CreateLinkRequest(
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
                null,
                null,
                null,
                null
        );
        CreateLinkRequest req2 = new CreateLinkRequest(
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
                null,
                null,
                null,
                null
        );

        LinkDto a = shortLinkService.create(TENANT_ID, CreatedBy.user(USER_ID), req1);
        LinkDto b = shortLinkService.create(TENANT_ID, CreatedBy.user(USER_ID), req2);

        assertThat(a.code()).isEqualTo("Abcdef");
        assertThat(b.code()).isEqualTo("abcdef");

        ShortLinkEntity linkA = shortLinkQueryMapper.findUnscopedByCode("Abcdef");
        assertThat(linkA).isNotNull();
        assertThat(linkA.getCode()).isEqualTo("Abcdef");

        ShortLinkEntity linkB = shortLinkQueryMapper.findUnscopedByCode("abcdef");
        assertThat(linkB).isNotNull();
        assertThat(linkB.getCode()).isEqualTo("abcdef");
        assertThat(linkA.getId()).isNotEqualTo(linkB.getId());

        assertThat(redirectService.resolve("Abcdef").originalUrl()).isEqualTo("https://example.com/a");
        assertThat(redirectService.resolve("abcdef").originalUrl()).isEqualTo("https://example.com/b");
        assertThat(redis.opsForValue().get(key("Abcdef"))).isNotNull();
        assertThat(redis.opsForValue().get(key("abcdef"))).isNotNull();
    }

    @Test
    void unscopedCustomCode_shouldNotConflictWithSameCodeOnCustomDomain() {
        long suffix = System.nanoTime();
        String code = "route" + Long.toHexString(suffix);
        long appId = suffix + 11;
        long domainId = suffix + 21;

        insertApplication(appId, TENANT_ID, "app-" + suffix);
        insertDedicatedDomain(domainId, TENANT_ID, appId, "custom-" + suffix + ".example.test");
        insertScopedShortLink(suffix + 31, TENANT_ID, appId, domainId, code, "https://example.com/custom");

        LinkDto created = shortLinkService.create(
                TENANT_ID,
                CreatedBy.user(USER_ID),
                new CreateLinkRequest(
                        "https://example.com/base",
                        null,
                        null,
                        null,
                        code,
                        Set.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        assertThat(created.code()).isEqualTo(code);
        ShortLinkEntity unscoped = shortLinkQueryMapper.findUnscopedByCode(code);
        assertThat(unscoped).isNotNull();
        assertThat(unscoped.getDomainId()).isNull();
        assertThat(unscoped.getOriginalUrl()).isEqualTo("https://example.com/base");
    }

    @Test
    void unscopedCustomCode_shouldBeUniqueAtDatabaseLevel() {
        long suffix = System.nanoTime();
        String code = "plain" + Long.toHexString(suffix);

        insertUnscopedShortLink(suffix + 41, TENANT_ID, code, "https://example.com/one");

        assertThatThrownBy(() -> insertUnscopedShortLink(suffix + 42, TENANT_ID, code, "https://example.com/two"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private void insertApplication(long applicationId, long tenantId, String applicationKey) {
        jdbcTemplate.update(
                """
                        INSERT INTO applications (id, tenant_id, application_key, display_name, status)
                        VALUES (?, ?, ?, ?, 'ACTIVE')
                        """,
                applicationId,
                tenantId,
                applicationKey,
                applicationKey
        );
    }

    private void insertDedicatedDomain(long domainId, long tenantId, long applicationId, String hostname) {
        jdbcTemplate.update(
                """
                        INSERT INTO domains (id, tenant_id, application_id, hostname, scope, status, trust_class)
                        VALUES (?, ?, ?, ?, 'APPLICATION_DEDICATED', 'ACTIVE', 'FIRST_PARTY')
                        """,
                domainId,
                tenantId,
                applicationId,
                hostname
        );
    }

    private void insertScopedShortLink(
            long linkId,
            long tenantId,
            long applicationId,
            long domainId,
            String code,
            String originalUrl
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO short_links (
                            id,
                            tenant_id,
                            application_id,
                            domain_id,
                            code,
                            lifecycle_state,
                            original_url,
                            note,
                            enabled,
                            expires_at,
                            archived_at,
                            redirect_status_code,
                            preview_enabled,
                            unavailable_landing_url,
                            query_forward_mode,
                            query_forward_allowlist,
                            created_by_type,
                            created_by,
                            version
                        ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, NULL, 1, NULL, NULL, 302, 0, NULL, NULL, NULL, 'USER', 1, 0)
                        """,
                linkId,
                tenantId,
                applicationId,
                domainId,
                code,
                originalUrl
        );
    }

    private void insertUnscopedShortLink(
            long linkId,
            long tenantId,
            String code,
            String originalUrl
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO short_links (
                            id,
                            tenant_id,
                            application_id,
                            domain_id,
                            code,
                            lifecycle_state,
                            original_url,
                            note,
                            enabled,
                            expires_at,
                            archived_at,
                            redirect_status_code,
                            preview_enabled,
                            unavailable_landing_url,
                            query_forward_mode,
                            query_forward_allowlist,
                            created_by_type,
                            created_by,
                            version
                        ) VALUES (?, ?, NULL, NULL, ?, 'ACTIVE', ?, NULL, 1, NULL, NULL, 302, 0, NULL, NULL, NULL, 'USER', 1, 0)
                        """,
                linkId,
                tenantId,
                code,
                originalUrl
        );
    }

    private static String key(String code) {
        return "link:code:" + code;
    }
}
