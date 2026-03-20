package com.linkforge.shortlink.interfaces.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.LinkForgeApplication;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.shortlink.application.ShortLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.scheduling.enabled=false"
)
@AutoConfigureMockMvc
class ShortLinkPaginationApiIntegrationTest {

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

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void listEndpoints_shouldPreservePageResponseShape() throws Exception {
        RegisteredPrincipal principal = registerTenantAdmin();
        AppDomainFixture fixture = provisionDedicatedApplication(
                principal.tenantId(),
                "pagination-links-" + principal.tenantId(),
                "pagination-links-" + principal.tenantId() + ".example.test"
        );
        String apiKey = createApiKey(principal.token(), fixture.applicationId());

        JsonNode first = createScopedLink(principal.token(), fixture, "https://example.com/pagination/a", "page-a");
        JsonNode second = createScopedLink(principal.token(), fixture, "https://example.com/pagination/b", "page-b");
        String firstIdentity = linkIdentity(first.get("data"));
        String secondIdentity = linkIdentity(second.get("data"));

        JsonNode jwtList = getJson(
                get("/api/v1/links")
                        .header("Authorization", "Bearer " + principal.token())
                        .param("page", "1")
                        .param("size", "1")
        );
        assertThat(jwtList.get("code").asInt()).isEqualTo(0);
        assertThat(jwtList.get("requestId").asText()).isNotBlank();
        JsonNode jwtData = jwtList.get("data");
        assertThat(jwtData.get("items")).hasSize(1);
        assertThat(jwtData.get("total").asLong()).isEqualTo(2L);
        assertThat(jwtData.get("page").asInt()).isEqualTo(1);
        assertThat(jwtData.get("size").asInt()).isEqualTo(1);
        assertThat(linkIdentity(jwtData.get("items").get(0))).isIn(firstIdentity, secondIdentity);

        JsonNode openList = getJson(
                get("/api/v1/open/links")
                        .header("X-API-Key", apiKey)
                        .param("page", "-3")
                        .param("size", "200")
        );
        assertThat(openList.get("code").asInt()).isEqualTo(0);
        assertThat(openList.get("requestId").asText()).isNotBlank();
        JsonNode openData = openList.get("data");
        assertThat(openData.get("items")).hasSize(2);
        assertThat(openData.get("total").asLong()).isEqualTo(2L);
        assertThat(openData.get("page").asInt()).isEqualTo(0);
        assertThat(openData.get("size").asInt()).isEqualTo(100);
        assertThat(List.of(
                linkIdentity(openData.get("items").get(0)),
                linkIdentity(openData.get("items").get(1))
        )).containsExactlyInAnyOrder(firstIdentity, secondIdentity);
    }

    @Test
    void listEndpoints_shouldNormalizeNonPositiveSize() throws Exception {
        RegisteredPrincipal principal = registerTenantAdmin();
        AppDomainFixture fixture = provisionDedicatedApplication(
                principal.tenantId(),
                "pagination-zero-" + principal.tenantId(),
                "pagination-zero-" + principal.tenantId() + ".example.test"
        );
        String apiKey = createApiKey(principal.token(), fixture.applicationId());
        createScopedLink(principal.token(), fixture, "https://example.com/pagination/zero-size", "page-zero");

        JsonNode jwtList = getJson(
                get("/api/v1/links")
                        .header("Authorization", "Bearer " + principal.token())
                        .param("page", "0")
                        .param("size", "0")
        );
        assertThat(jwtList.get("code").asInt()).isEqualTo(0);
        JsonNode jwtData = jwtList.get("data");
        assertThat(jwtData.get("items")).hasSize(1);
        assertThat(jwtData.get("total").asLong()).isEqualTo(1L);
        assertThat(jwtData.get("page").asInt()).isEqualTo(0);
        assertThat(jwtData.get("size").asInt()).isEqualTo(1);

        JsonNode openList = getJson(
                get("/api/v1/open/links")
                        .header("X-API-Key", apiKey)
                        .param("page", "0")
                        .param("size", "-7")
        );
        assertThat(openList.get("code").asInt()).isEqualTo(0);
        JsonNode openData = openList.get("data");
        assertThat(openData.get("items")).hasSize(1);
        assertThat(openData.get("total").asLong()).isEqualTo(1L);
        assertThat(openData.get("page").asInt()).isEqualTo(0);
        assertThat(openData.get("size").asInt()).isEqualTo(1);
    }

    @Test
    void application_scoped_link_api_should_reject_access_to_other_application_in_same_tenant() throws Exception {
        RegisteredPrincipal principal = registerTenantAdmin();
        AppDomainFixture firstApp = provisionDedicatedApplication(
                principal.tenantId(),
                "scope-a-" + principal.tenantId(),
                "scope-a-" + principal.tenantId() + ".example.test"
        );
        AppDomainFixture secondApp = provisionDedicatedApplication(
                principal.tenantId(),
                "scope-b-" + principal.tenantId(),
                "scope-b-" + principal.tenantId() + ".example.test"
        );
        String firstAppApiKey = createApiKey(principal.token(), firstApp.applicationId());

        JsonNode firstLinkBody = objectMapper.createObjectNode()
                .put("originalUrl", "https://example.com/app-scope/a")
                .put("note", "scope-a")
                .put("applicationId", firstApp.applicationId())
                .put("domainId", firstApp.domainId());
        JsonNode secondLinkBody = objectMapper.createObjectNode()
                .put("originalUrl", "https://example.com/app-scope/b")
                .put("note", "scope-b")
                .put("applicationId", secondApp.applicationId())
                .put("domainId", secondApp.domainId());

        JsonNode firstCreated = postJson("/api/v1/links", firstLinkBody, principal.token(), null);
        JsonNode secondCreated = postJson("/api/v1/links", secondLinkBody, principal.token(), null);
        assertThat(firstCreated.get("code").asInt()).isEqualTo(0);
        assertThat(secondCreated.get("code").asInt()).isEqualTo(0);

        JsonNode scopedList = getJson(
                get("/api/v1/open/applications/" + firstApp.applicationId() + "/links")
                        .header("X-API-Key", firstAppApiKey)
                        .param("page", "0")
                        .param("size", "20")
        );
        assertThat(scopedList.get("code").asInt()).isEqualTo(0);
        JsonNode scopedData = scopedList.get("data");
        assertThat(scopedData.get("total").asLong()).isEqualTo(1L);
        assertThat(scopedData.get("items")).hasSize(1);
        assertThat(scopedData.get("items").get(0).get("applicationId").asLong()).isEqualTo(firstApp.applicationId());
        assertThat(scopedData.get("items").get(0).get("domainId").asLong()).isEqualTo(firstApp.domainId());

        JsonNode forbidden = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/open/applications/" + secondApp.applicationId() + "/links")
                                .header("X-API-Key", firstAppApiKey)
                )
                .andExpect(status().isForbidden())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(forbidden.get("code").asInt()).isEqualTo(40300);
        assertThat(forbidden.get("message").asText()).contains("应用");
    }

    @Test
    void application_scoped_jwt_link_api_should_require_tenant_admin() throws Exception {
        RegisteredPrincipal admin = registerTenantAdmin();
        AppDomainFixture fixture = provisionDedicatedApplication(
                admin.tenantId(),
                "jwt-scope-admin-" + admin.tenantId(),
                "jwt-scope-admin-" + admin.tenantId() + ".example.test"
        );
        String userToken = createRegularUserToken(admin.token(), "jwt-user-" + System.nanoTime() + "@example.com");

        JsonNode createLinkBody = objectMapper.createObjectNode()
                .put("originalUrl", "https://example.com/user-scope")
                .put("applicationId", fixture.applicationId())
                .put("domainId", fixture.domainId());

        mockMvc.perform(
                        post("/api/v1/applications/" + fixture.applicationId() + "/links")
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createLinkBody))
                )
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        get("/api/v1/applications/" + fixture.applicationId() + "/links")
                                .header("Authorization", "Bearer " + userToken)
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void listEndpoints_shouldRejectHugePageToAvoidDeepOffsetPagination() throws Exception {
        RegisteredPrincipal principal = registerTenantAdmin();
        AppDomainFixture fixture = provisionDedicatedApplication(
                principal.tenantId(),
                "pagination-huge-" + principal.tenantId(),
                "pagination-huge-" + principal.tenantId() + ".example.test"
        );
        String apiKey = createApiKey(principal.token(), fixture.applicationId());
        createScopedLink(principal.token(), fixture, "https://example.com/pagination/huge-page", "page-huge");

        String huge = String.valueOf(Integer.MAX_VALUE);

        JsonNode jwt = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/links")
                                .header("Authorization", "Bearer " + principal.token())
                                .param("page", huge)
                                .param("size", "100")
                )
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(jwt.get("code").asInt()).isEqualTo(40000);
        assertThat(jwt.get("message").asText()).contains("分页参数过大");

        JsonNode open = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/open/links")
                                .header("X-API-Key", apiKey)
                                .param("page", huge)
                                .param("size", "100")
                )
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(open.get("code").asInt()).isEqualTo(40000);
        assertThat(open.get("message").asText()).contains("分页参数过大");
    }

    @Test
    void pageQuery_shouldEnforceConstructionInvariants() {
        assertThat(new PageQuery(-3, 0)).isEqualTo(new PageQuery(0, 1));
        assertThat(new PageQuery(2, -7)).isEqualTo(new PageQuery(2, 1));
        assertThat(PageQuery.of(-5, 500, 100)).isEqualTo(new PageQuery(0, 100));
    }

    @Test
    void pagingContracts_shouldNotExposeSpringDataTypes() {
        assertNoSpringDataPagingLeak(ShortLinkController.class, "list", "exportCsv");
        assertNoSpringDataPagingLeak(OpenApiShortLinkController.class, "list");
        assertNoSpringDataPagingLeak(ShortLinkService.class, "search", "exportCsv");
    }

    private void assertNoSpringDataPagingLeak(Class<?> type, String... methodNames) {
        assertThat(Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Arrays.asList(methodNames).contains(method.getName()))
                .toList()
        )
                .isNotEmpty()
                .allSatisfy(this::assertNoSpringDataPagingLeak);
    }

    private void assertNoSpringDataPagingLeak(Method method) {
        assertThat(method.getReturnType().getPackageName())
                .as("return type of %s", method)
                .doesNotStartWith("org.springframework.data.domain");
        assertThat(method.getParameterTypes())
                .as("parameter types of %s", method)
                .allSatisfy(parameterType -> assertThat(parameterType.getPackageName())
                        .doesNotStartWith("org.springframework.data.domain"));
    }

    private RegisteredPrincipal registerTenantAdmin() throws Exception {
        String suffix = Long.toUnsignedString(System.nanoTime());
        JsonNode registerBody = objectMapper.createObjectNode()
                .put("tenantName", "tenant-" + suffix)
                .put("email", "admin-" + suffix + "@example.com")
                .put("password", "password123");
        JsonNode register = postJson("/api/v1/auth/register", registerBody, null, null);
        assertThat(register.get("code").asInt()).isEqualTo(0);
        return new RegisteredPrincipal(
                register.get("data").get("token").asText(),
                register.get("data").get("user").get("tenantId").asLong()
        );
    }

    private String createRegularUserToken(String adminToken, String email) throws Exception {
        var createUserBody = objectMapper.createObjectNode()
                .put("email", email)
                .put("password", "password123");
        createUserBody.putArray("roles").add("USER");
        JsonNode createUser = postJson("/api/v1/users", createUserBody, adminToken, null);
        assertThat(createUser.get("code").asInt()).isEqualTo(0);

        JsonNode loginBody = objectMapper.createObjectNode()
                .put("email", email)
                .put("password", "password123");
        JsonNode login = postJson("/api/v1/auth/login", loginBody, null, null);
        assertThat(login.get("code").asInt()).isEqualTo(0);
        return login.get("data").get("token").asText();
    }

    private String createApiKey(String token) throws Exception {
        RegisteredPrincipal principal = registerTenantAdminForToken(token);
        long applicationId = provisionApplication(principal.tenantId(), "pagination-key-app-" + principal.tenantId());
        return createApiKey(token, applicationId);
    }

    private String createApiKey(String token, long applicationId) throws Exception {
        JsonNode createKeyBody = objectMapper.createObjectNode()
                .put("applicationId", applicationId)
                .put("name", "pagination-key");
        JsonNode response = postJson("/api/v1/api-keys", createKeyBody, token, null);
        assertThat(response.get("code").asInt()).isEqualTo(0);
        return response.get("data").get("apiKey").asText();
    }

    private RegisteredPrincipal registerTenantAdminForToken(String token) {
        return new RegisteredPrincipal(token, readTenantId(token));
    }

    private long readTenantId(String token) {
        try {
            String me = mockMvc.perform(
                            get("/api/v1/me")
                                    .header("Authorization", "Bearer " + token)
                    )
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode json = objectMapper.readTree(me);
            return json.get("data").get("tenantId").asLong();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private long provisionApplication(long tenantId, String applicationKey) {
        long applicationId = Math.abs(System.nanoTime()) + 20_000;
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
        jdbcTemplate.update(
                """
                        INSERT INTO application_policies (application_id, default_domain_scope, default_redirect_status_code, preview_enabled)
                        VALUES (?, 'TENANT_SHARED', 302, 0)
                        """,
                applicationId
        );
        jdbcTemplate.update(
                """
                        INSERT INTO application_quotas (application_id, monthly_link_limit, monthly_click_limit)
                        VALUES (?, 10000, 1000000)
                        """,
                applicationId
        );
        return applicationId;
    }

    private AppDomainFixture provisionDedicatedApplication(long tenantId, String applicationKey, String hostname) {
        long applicationId = Math.abs(System.nanoTime()) + 20_000;
        long domainId = applicationId + 5_000;
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
        jdbcTemplate.update(
                """
                        INSERT INTO application_policies (application_id, default_domain_scope, default_redirect_status_code, preview_enabled)
                        VALUES (?, 'APPLICATION_DEDICATED', 302, 0)
                        """,
                applicationId
        );
        jdbcTemplate.update(
                """
                        INSERT INTO application_quotas (application_id, monthly_link_limit, monthly_click_limit)
                        VALUES (?, 10000, 1000000)
                        """,
                applicationId
        );
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
        return new AppDomainFixture(applicationId, domainId);
    }

    private JsonNode createLink(String token, String originalUrl, String note) throws Exception {
        JsonNode createLinkBody = objectMapper.createObjectNode()
                .put("originalUrl", originalUrl)
                .put("note", note);
        JsonNode response = postJson("/api/v1/links", createLinkBody, token, null);
        assertThat(response.get("code").asInt()).isEqualTo(0);
        return response;
    }

    private JsonNode createScopedLink(String token, AppDomainFixture fixture, String originalUrl, String note) throws Exception {
        JsonNode createLinkBody = objectMapper.createObjectNode()
                .put("originalUrl", originalUrl)
                .put("note", note)
                .put("applicationId", fixture.applicationId())
                .put("domainId", fixture.domainId());
        JsonNode response = postJson("/api/v1/applications/" + fixture.applicationId() + "/links", createLinkBody, token, null);
        assertThat(response.get("code").asInt()).isEqualTo(0);
        return response;
    }

    private String linkIdentity(JsonNode link) {
        return link.get("id").asLong() + ":" + link.get("code").asText();
    }

    private JsonNode postJson(String path, JsonNode body, String bearerToken, String apiKey) throws Exception {
        var request = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        if (apiKey != null) {
            request.header("X-API-Key", apiKey);
        }
        return getJson(request);
    }

    private JsonNode getJson(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        String content = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content);
    }

    private record RegisteredPrincipal(String token, long tenantId) {
    }

    private record AppDomainFixture(long applicationId, long domainId) {
    }
}
