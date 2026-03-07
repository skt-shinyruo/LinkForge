package com.linkforge.shortlink.application;

import com.linkforge.LinkForgeApplication;
import com.linkforge.foundation.security.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.scheduling.enabled=false"
)
class ShortLinkDeleteRetentionIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("linkforge")
            .withUsername("linkforge")
            .withPassword("linkforge");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2.4-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);

        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // 测试环境固定密钥，避免启动失败
        r.add("app.security.jwt.secret", () -> "test-secret-please-change-but-long-enough-32-bytes");
        r.add("app.analytics.salt", () -> "test-analytics-salt");

        // 避免统计相关调度影响测试稳定性
        r.add("app.analytics.dimensions.enabled", () -> "false");
        r.add("app.analytics.events.enabled", () -> "false");
    }

    @Autowired
    ShortLinkService shortLinkService;

    @Autowired
    JdbcTemplate jdbcTemplate;

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
    void delete_shouldNotDeleteAnalyticsRows() {
        ShortLinkService.CreateLinkRequest req = new ShortLinkService.CreateLinkRequest(
                "https://example.com",
                "note",
                null,
                null,
                null,
                Set.of(),
                null,
                null,
                null,
                null,
                null
        );
        ShortLinkService.LinkDto created = shortLinkService.create(TENANT_ID, USER_ID, req);
        long linkId = created.id();

        LocalDate day = LocalDate.of(2026, 1, 1);
        jdbcTemplate.update(
                "INSERT INTO link_stats_daily (link_id, tenant_id, day, pv, uv) VALUES (?, ?, ?, ?, ?)",
                linkId, TENANT_ID, Date.valueOf(day), 10L, 5L
        );
        jdbcTemplate.update(
                """
                        INSERT INTO link_stats_dim_daily
                        (tenant_id, link_id, day, dim_type, dim_value, pv, uv)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                TENANT_ID, linkId, Date.valueOf(day), "referer_domain", "example.com", 3L, 2L
        );
        jdbcTemplate.update(
                """
                        INSERT INTO link_visit_events
                        (id, tenant_id, link_id, occurred_at, request_id)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                900_000_001L, TENANT_ID, linkId, LocalDateTime.of(2026, 1, 1, 0, 0), "req-1"
        );

        shortLinkService.archive(TENANT_ID, linkId);
        shortLinkService.delete(TENANT_ID, linkId);

        Integer shortLinks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM short_links WHERE tenant_id = ? AND id = ?",
                Integer.class,
                TENANT_ID,
                linkId
        );
        assertThat(shortLinks).isEqualTo(0);

        Integer daily = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM link_stats_daily WHERE tenant_id = ? AND link_id = ?",
                Integer.class,
                TENANT_ID,
                linkId
        );
        assertThat(daily).isEqualTo(1);

        Integer dimDaily = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM link_stats_dim_daily WHERE tenant_id = ? AND link_id = ?",
                Integer.class,
                TENANT_ID,
                linkId
        );
        assertThat(dimDaily).isEqualTo(1);

        Integer events = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM link_visit_events WHERE tenant_id = ? AND link_id = ?",
                Integer.class,
                TENANT_ID,
                linkId
        );
        assertThat(events).isEqualTo(1);
    }
}

