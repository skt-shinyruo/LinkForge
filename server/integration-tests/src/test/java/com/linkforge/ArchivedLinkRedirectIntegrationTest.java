package com.linkforge;

import com.linkforge.LinkForgeApplication;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkCommandMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ArchivedLinkRedirectIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("linkforge")
            .withUsername("linkforge")
            .withPassword("linkforge");

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
    ShortLinkCommandMapper shortLinkCommandMapper;

    private String code;

    @BeforeEach
    void setUp() {
        // 归档短链：Edge 侧应视为不可用（表现为 404 not found）
        long suffix = System.nanoTime();
        code = "archived" + Long.toUnsignedString(suffix);

        ShortLinkEntity link = new ShortLinkEntity();
        long id = suffix & Long.MAX_VALUE;
        link.setId(id <= 0 ? 1L : id);
        link.setTenantId(1L);
        link.setCode(code);
        link.setOriginalUrl("https://example.com");
        link.setNote(null);
        link.setEnabled(true);
        link.setExpiresAt(null);
        link.setArchivedAt(java.time.LocalDateTime.of(2026, 2, 20, 0, 0));
        link.setRedirectStatusCode(null);
        link.setPreviewEnabled(false);
        link.setUnavailableLandingUrl(null);
        link.setQueryForwardMode(null);
        link.setQueryForwardAllowlist(null);
        link.setCreatedBy(1L);

        shortLinkCommandMapper.insert(link);
    }

    @Test
    void should_return_404_html_when_link_archived() throws Exception {
        mockMvc.perform(get("/r/" + code).header(HttpHeaders.ACCEPT, "text/html"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}
