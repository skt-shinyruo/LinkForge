package com.linkforge;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.shortlink.application.ShortLinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.scheduling.enabled=false"
)
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
    StringRedisTemplate redis;

    @Autowired
    ShortLinkService shortLinkService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String code;
    private long linkId;
    private String host;
    private String originalUrl;

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, TENANT_ID);

        long suffix = System.nanoTime();
        code = "archived" + Long.toUnsignedString(suffix);
        host = "archived-" + suffix + ".example.test";
        originalUrl = "https://example.com/" + code;

        long applicationId = suffix + 11;
        long domainId = suffix + 21;

        insertApplication(applicationId, TENANT_ID, "app-" + suffix, "App " + suffix);
        insertDedicatedDomain(domainId, TENANT_ID, applicationId, host);

        ShortLinkService.LinkDto created = shortLinkService.create(
                TENANT_ID,
                ShortLinkService.CreatedBy.user(USER_ID),
                new ShortLinkService.CreateLinkRequest(
                        originalUrl,
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
                        applicationId,
                        domainId,
                        null
                )
        );
        linkId = created.id();
    }

    @Test
    void should_return_404_html_when_link_archived() throws Exception {
        mockMvc.perform(get("/r/" + code)
                        .with(host(host))
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, originalUrl));

        assertThat(redis.opsForValue().get(key(host, code))).isNotNull();

        shortLinkService.archive(TENANT_ID, linkId);
        assertThat(redis.opsForValue().get(key(host, code))).isNull();

        mockMvc.perform(get("/r/" + code)
                        .with(host(host))
                        .header(HttpHeaders.ACCEPT, "text/html"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));

        assertThat(redis.opsForValue().get(key(host, code))).isNotNull();
    }

    private void insertApplication(long applicationId, long tenantId, String applicationKey, String displayName) {
        jdbcTemplate.update(
                """
                        INSERT INTO applications (id, tenant_id, application_key, display_name, status)
                        VALUES (?, ?, ?, ?, 'ACTIVE')
                        """,
                applicationId,
                tenantId,
                applicationKey,
                displayName
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

    private static String key(String host, String code) {
        return "link:host:" + host + ":code:" + code;
    }

    private static RequestPostProcessor host(String hostname) {
        return request -> {
            request.setServerName(hostname);
            request.addHeader(HttpHeaders.HOST, hostname);
            return request;
        };
    }
}
