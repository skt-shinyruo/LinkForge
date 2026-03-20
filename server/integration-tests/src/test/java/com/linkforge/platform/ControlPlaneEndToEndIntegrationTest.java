package com.linkforge.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.LinkForgeApplication;
import com.linkforge.analytics.infrastructure.catalog.ShortLinkCatalogProjectorJob;
import com.linkforge.analytics.infrastructure.job.AnalyticsFlushJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ControlPlaneEndToEndIntegrationTest {

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
        r.add("app.analytics.dimensions.enabled", () -> "false");
        r.add("app.analytics.events.enabled", () -> "false");
        r.add("app.analytics.events.sample-rate", () -> "1");
        r.add("APP_ANALYTICS_FLUSH_DELAY_MS", () -> "9999999");
        r.add("APP_ANALYTICS_SHORTLINK_CATALOG_PROJECTOR_DELAY_MS", () -> "9999999");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    AnalyticsFlushJob analyticsFlushJob;

    @Autowired
    ShortLinkCatalogProjectorJob shortLinkCatalogProjectorJob;

    @BeforeEach
    void resetRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void tenant_admin_should_onboard_application_request_sensitive_change_get_approved_and_serve_public_redirect() throws Exception {
        RegisteredPrincipal owner = registerTenantAdmin("cp-e2e-owner");

        JsonNode application = postJson(
                "/api/v1/applications",
                objectMapper.createObjectNode()
                        .put("applicationKey", "order-center")
                        .put("displayName", "Order Center"),
                owner.token(),
                null
        );
        long applicationId = application.get("data").get("id").asLong();

        String hostname = "order-center-" + owner.tenantId() + ".example.test";
        JsonNode domain = postJson(
                "/api/v1/domains/tenant-shared",
                objectMapper.createObjectNode().put("hostname", hostname),
                owner.token(),
                null
        );
        long domainId = domain.get("data").get("id").asLong();

        mockMvc.perform(
                        post("/api/v1/applications/" + applicationId + "/domain-authorizations/" + domainId)
                                .header("Authorization", "Bearer " + owner.token())
                )
                .andExpect(status().isOk());

        JsonNode apiKey = postJson(
                "/api/v1/api-keys",
                objectMapper.createObjectNode()
                        .put("applicationId", applicationId)
                        .put("name", "order-center-openapi"),
                owner.token(),
                null
        );
        String apiKeyValue = apiKey.get("data").get("apiKey").asText();

        JsonNode createdLink = postJson(
                "/api/v1/open/applications/" + applicationId + "/links",
                objectMapper.createObjectNode()
                        .put("originalUrl", "https://example.com/orders")
                        .put("domainId", domainId),
                null,
                apiKeyValue
        );
        long linkId = createdLink.get("data").get("id").asLong();
        String code = createdLink.get("data").get("code").asText();

        shortLinkCatalogProjectorJob.project();

        LocalDateTimeRange range = LocalDateTimeRange.lastDayUtc();
        JsonNode approvalRequest = postJson(
                "/api/v1/applications/" + applicationId + "/links/" + linkId + "/events/export-requests"
                        + "?from=" + range.from() + "&to=" + range.to(),
                null,
                owner.token(),
                null
        );
        long approvalId = approvalRequest.get("data").get("id").asLong();
        assertThat(approvalRequest.get("data").get("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(approvalRequest.get("data").get("targetApplicationId").asLong()).isEqualTo(applicationId);

        TenantUser approver = createTenantAdminUser(owner.token(), "approver-" + System.nanoTime() + "@example.com");
        JsonNode approved = postJson(
                "/api/v1/approvals/" + approvalId + "/approve",
                objectMapper.createObjectNode().put("reason", "approved by control plane"),
                approver.token(),
                null
        );
        assertThat(approved.get("data").get("status").asText()).isEqualTo("EXECUTED");

        mockMvc.perform(
                        get("/r/" + code)
                                .with(host(hostname))
                                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                )
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/orders"));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        seedStats(owner.tenantId(), linkId, today, 7, 3);
        analyticsFlushJob.flush();

        JsonNode appOverview = getJson(
                get("/api/v1/applications/" + applicationId + "/stats/overview")
                        .header("Authorization", "Bearer " + owner.token())
                        .param("from", today.toString())
                        .param("to", today.toString())
        );
        assertThat(appOverview.get("data")).hasSize(1);
        assertThat(appOverview.get("data").get(0).get("pv").asLong()).isGreaterThanOrEqualTo(7L);

        JsonNode auditLogs = getJson(
                get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + owner.token())
        );
        assertThat(auditLogs.get("data").toString()).contains("SUBMIT_REQUEST");
        assertThat(auditLogs.get("data").toString()).contains("APPROVE_REQUEST");
        assertThat(auditLogs.get("data").toString()).contains(String.valueOf(approvalId));
    }

    private RegisteredPrincipal registerTenantAdmin(String tenantNamePrefix) throws Exception {
        String suffix = Long.toUnsignedString(System.nanoTime());
        JsonNode registerBody = objectMapper.createObjectNode()
                .put("tenantName", tenantNamePrefix + "-" + suffix)
                .put("email", tenantNamePrefix + "-" + suffix + "@example.com")
                .put("password", "password123");
        JsonNode register = postJson("/api/v1/auth/register", registerBody, null, null);
        return new RegisteredPrincipal(
                register.get("data").get("token").asText(),
                register.get("data").get("user").get("tenantId").asLong()
        );
    }

    private TenantUser createTenantAdminUser(String ownerToken, String email) throws Exception {
        String password = "password123";
        JsonNode createUserBody = objectMapper.createObjectNode()
                .put("email", email)
                .put("password", password);
        ((com.fasterxml.jackson.databind.node.ObjectNode) createUserBody)
                .putArray("roles")
                .add("TENANT_ADMIN");
        JsonNode created = postJson(
                "/api/v1/users",
                createUserBody,
                ownerToken,
                null
        );
        long userId = created.get("data").get("id").asLong();
        JsonNode login = postJson(
                "/api/v1/auth/login",
                objectMapper.createObjectNode()
                        .put("email", email)
                        .put("password", password),
                null,
                null
        );
        return new TenantUser(userId, email, login.get("data").get("token").asText());
    }

    private void seedStats(long tenantId, long linkId, LocalDate day, long pv, long uv) {
        String dayRaw = day.format(DateTimeFormatter.BASIC_ISO_DATE);
        String pvKey = "stats:pv:" + tenantId + ":" + linkId + ":" + dayRaw;
        String uvKey = "stats:uv:" + tenantId + ":" + linkId + ":" + dayRaw;
        String dirtyStreamKey = "stats:dirty:flush:" + dayRaw;
        String dirtyMember = tenantId + ":" + linkId;

        for (int i = 0; i < pv; i++) {
            redis.opsForValue().increment(pvKey);
        }
        for (int i = 0; i < uv; i++) {
            redis.opsForHyperLogLog().add(uvKey, "v" + i + "-" + tenantId + "-" + linkId);
        }
        redis.opsForStream().add(StreamRecords.newRecord().in(dirtyStreamKey).ofStrings(java.util.Map.of(
                "member", dirtyMember,
                "ts", String.valueOf(System.currentTimeMillis())
        )));
    }

    private JsonNode postJson(String path, JsonNode body, String bearerToken, String apiKey) throws Exception {
        RequestBuilder request = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "" : objectMapper.writeValueAsString(body));
        return getJson(withAuth(request, bearerToken, apiKey));
    }

    private RequestBuilder withAuth(RequestBuilder request, String bearerToken, String apiKey) {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder =
                (org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) request;
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        if (apiKey != null) {
            builder.header("X-API-Key", apiKey);
        }
        return builder;
    }

    private JsonNode getJson(RequestBuilder request) throws Exception {
        String content = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content);
    }

    private static RequestPostProcessor host(String hostname) {
        return request -> {
            request.setServerName(hostname);
            request.addHeader(HttpHeaders.HOST, hostname);
            return request;
        };
    }

    private record RegisteredPrincipal(String token, long tenantId) {
    }

    private record TenantUser(long id, String email, String token) {
    }

    private record LocalDateTimeRange(String from, String to) {
        private static LocalDateTimeRange lastDayUtc() {
            java.time.LocalDateTime to = java.time.LocalDateTime.now(ZoneOffset.UTC);
            java.time.LocalDateTime from = to.minusDays(1);
            return new LocalDateTimeRange(from.toString(), to.toString());
        }
    }
}
