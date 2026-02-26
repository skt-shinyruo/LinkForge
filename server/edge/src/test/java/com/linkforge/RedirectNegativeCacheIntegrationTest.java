package com.linkforge;

import com.linkforge.edge.LinkForgeEdgeApplication;
import com.linkforge.edge.redirect.service.ShortLinkLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
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

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = LinkForgeEdgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RedirectNegativeCacheIntegrationTest {

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
        r.add("app.redirect.not-found-cache-ttl-seconds", () -> "60");
        r.add("app.edge.risk-control.enabled", () -> "false");

        // 预览页内部参数默认不透传（逗号分隔 List 绑定）
        r.add("app.redirect.query-forward-reserved-params", () -> "__lf_confirm,__lf_preview");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StringRedisTemplate redis;

    @SpyBean
    ShortLinkLookupRepository shortLinkLookupRepository;

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

        // 避免测试间缓存 key 干扰
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void not_found_should_be_negative_cached_to_reduce_db_lookups() throws Exception {
        mockMvc.perform(get("/r/missing").header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/r/missing").header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNotFound());

        verify(shortLinkLookupRepository, times(1)).findByCode("missing");
    }
}

