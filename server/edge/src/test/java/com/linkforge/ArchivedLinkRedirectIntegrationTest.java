package com.linkforge;

import com.linkforge.edge.LinkForgeEdgeApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = LinkForgeEdgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ArchivedLinkRedirectIntegrationTest {

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

        // 避免启动期严格校验失败（以及减少 log 噪音）
        r.add("app.analytics.salt", () -> "test-analytics-salt");
        r.add("app.redirect.cache-ttl-seconds", () -> "60");
        r.add("app.redirect.default-status-code", () -> "302");
        r.add("app.edge.risk-control.enabled", () -> "false");

        // 预览页内部参数默认不透传（逗号分隔 List 绑定）
        r.add("app.redirect.query-forward-reserved-params", () -> "__lf_confirm,__lf_preview");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS short_links");
        jdbcTemplate.execute(
                """
                        CREATE TABLE short_links (
                          id BIGINT PRIMARY KEY,
                          tenant_id BIGINT NOT NULL,
                          code VARCHAR(32) NOT NULL,
                          original_url TEXT NOT NULL,
                          enabled TINYINT(1) NOT NULL,
                          expires_at DATETIME NULL,
                          archived_at DATETIME NULL,
                          redirect_status_code INT NULL,
                          preview_enabled TINYINT(1) NOT NULL,
                          unavailable_landing_url TEXT NULL,
                          query_forward_mode VARCHAR(16) NULL,
                          query_forward_allowlist VARCHAR(1024) NULL
                        )
                        """
        );

        // 归档短链：Edge 侧应视为不可用（表现为 404 not found）
        jdbcTemplate.update(
                """
                        INSERT INTO short_links (
                          id, tenant_id, code, original_url, enabled, expires_at, archived_at,
                          redirect_status_code, preview_enabled, unavailable_landing_url, query_forward_mode, query_forward_allowlist
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                1L,
                1L,
                "abc",
                "https://example.com",
                1,
                null,
                java.sql.Timestamp.valueOf("2026-02-20 00:00:00"),
                null,
                0,
                null,
                null,
                null
        );
    }

    @Test
    void should_return_404_html_when_link_archived() throws Exception {
        mockMvc.perform(get("/r/abc").header(HttpHeaders.ACCEPT, "text/html"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}

