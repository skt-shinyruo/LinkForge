package com.linkforge.redirect;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.scheduling.enabled=false"
)
@AutoConfigureMockMvc
class HostCodeRedirectIntegrationTest {

    private static final String LEGACY_BASE_HOST = "go.example.test";

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

        r.add("app.security.jwt.secret", () -> "test-secret-please-change-but-long-enough-32-bytes");
        r.add("app.analytics.salt", () -> "test-analytics-salt");
        r.add("app.base-url", () -> "https://" + LEGACY_BASE_HOST);
        r.add("app.edge.risk-control.enabled", () -> "false");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void same_code_under_different_domains_should_resolve_to_different_links() throws Exception {
        long tenantId = 101L;
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, tenantId);

        long suffix = System.nanoTime();
        String code = "shared" + Long.toHexString(suffix);

        long appOneId = suffix + 11;
        long appTwoId = suffix + 12;
        long domainOneId = suffix + 21;
        long domainTwoId = suffix + 22;
        long linkOneId = suffix + 31;
        long linkTwoId = suffix + 32;

        insertApplication(appOneId, tenantId, "app-one-" + suffix, "App One");
        insertApplication(appTwoId, tenantId, "app-two-" + suffix, "App Two");
        insertDedicatedDomain(domainOneId, tenantId, appOneId, "alpha-" + suffix + ".example.test");
        insertDedicatedDomain(domainTwoId, tenantId, appTwoId, "beta-" + suffix + ".example.test");

        insertShortLink(linkOneId, tenantId, appOneId, domainOneId, code, "https://example.com/alpha/" + suffix);
        insertShortLink(linkTwoId, tenantId, appTwoId, domainTwoId, code, "https://example.com/beta/" + suffix);

        mockMvc.perform(get("/r/" + code)
                        .with(host("alpha-" + suffix + ".example.test"))
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/alpha/" + suffix));

        mockMvc.perform(get("/r/" + code)
                        .with(host("beta-" + suffix + ".example.test"))
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/beta/" + suffix));
    }

    @Test
    void legacy_default_domain_should_continue_to_resolve_migrated_links_during_compatibility_window() throws Exception {
        long tenantId = 102L;
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, tenantId);

        long suffix = System.nanoTime();
        String code = "legacy" + Long.toHexString(suffix);

        long legacyAppId = suffix + 101;
        long customAppId = suffix + 102;
        long legacyDomainId = suffix + 111;
        long customDomainId = suffix + 112;
        long legacyLinkId = suffix + 121;
        long customLinkId = suffix + 122;

        insertApplication(legacyAppId, tenantId, "legacy-default", "Legacy Default");
        insertApplication(customAppId, tenantId, "campaign-" + suffix, "Campaign");
        insertDedicatedDomain(legacyDomainId, tenantId, legacyAppId, "legacy-" + tenantId + "." + LEGACY_BASE_HOST);
        insertDedicatedDomain(customDomainId, tenantId, customAppId, "campaign-" + suffix + ".example.test");

        insertShortLink(legacyLinkId, tenantId, legacyAppId, legacyDomainId, code, "https://example.com/legacy/" + suffix);
        insertShortLink(customLinkId, tenantId, customAppId, customDomainId, code, "https://example.com/campaign/" + suffix);

        mockMvc.perform(get("/r/" + code)
                        .with(host(LEGACY_BASE_HOST))
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/legacy/" + suffix));
    }

    @Test
    void legacy_base_host_should_not_fallback_to_non_legacy_domain_link() throws Exception {
        long tenantId = 103L;
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, tenantId);

        long suffix = System.nanoTime();
        String code = "legacymiss" + Long.toHexString(suffix);

        long appId = suffix + 201;
        long domainId = suffix + 211;
        long linkId = suffix + 221;
        String customHost = "campaign-" + suffix + ".example.test";

        insertApplication(appId, tenantId, "campaign-" + suffix, "Campaign");
        insertDedicatedDomain(domainId, tenantId, appId, customHost);
        insertShortLink(linkId, tenantId, appId, domainId, code, "https://example.com/campaign-only/" + suffix);

        mockMvc.perform(get("/r/" + code)
                        .with(host(LEGACY_BASE_HOST))
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/r/" + code)
                        .with(host(LEGACY_BASE_HOST))
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/r/" + code)
                        .with(host(customHost))
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/campaign-only/" + suffix));
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

    private void insertShortLink(
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

    private static RequestPostProcessor host(String hostname) {
        return request -> {
            request.setServerName(hostname);
            request.addHeader(HttpHeaders.HOST, hostname);
            return request;
        };
    }
}
