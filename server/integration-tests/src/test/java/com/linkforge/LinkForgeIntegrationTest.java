package com.linkforge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.app.config.MybatisConfig;
import com.linkforge.analytics.infrastructure.job.AnalyticsDimensionFlushJob;
import com.linkforge.analytics.infrastructure.job.AnalyticsEventIngestJob;
import com.linkforge.analytics.infrastructure.job.AnalyticsFlushJob;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.contract.openapi.OpenApiErrorCode;
import com.linkforge.governance.interfaces.web.ApprovalController;
import com.linkforge.governance.interfaces.web.AuditController;
import com.linkforge.platform.interfaces.web.TenantAdminApplicationController;
import com.linkforge.platform.interfaces.web.TenantAdminDomainController;
import com.linkforge.testsupport.SharedIntegrationTestSupport;
import org.mybatis.spring.annotation.MapperScan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class LinkForgeIntegrationTestSupport extends SharedIntegrationTestSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        // 访问明细 + 维度聚合测试开关（避免调度影响测试稳定性）
        r.add("app.analytics.dimensions.enabled", () -> "true");
        r.add("app.analytics.events.enabled", () -> "true");
        r.add("app.analytics.events.sample-rate", () -> "1");
        r.add("APP_ANALYTICS_EVENT_INGEST_DELAY_MS", () -> "9999999");
        r.add("APP_ANALYTICS_EVENT_RETENTION_DELAY_MS", () -> "9999999");
        r.add("APP_ANALYTICS_DIM_FLUSH_DELAY_MS", () -> "9999999");

        r.add("APP_ANALYTICS_FLUSH_DELAY_MS", () -> "9999999");

    }
}

@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class LinkForgeIntegrationTest extends LinkForgeIntegrationTestSupport {

    @Autowired
    ConfigurableApplicationContext applicationContext;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AnalyticsFlushJob analyticsFlushJob;

    @Autowired
    AnalyticsDimensionFlushJob analyticsDimensionFlushJob;

    @Autowired
    AnalyticsEventIngestJob analyticsEventIngestJob;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void applicationStarts_withMyBatisBootstrap_and_withoutExplicitJpaHooks() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.isActive()).isTrue();
        assertThat(applicationContext.containsBean("sqlSessionFactory"))
                .as("MyBatis SqlSessionFactory bean should be registered")
                .isTrue();
        String enableJpaRepositories = "org.springframework.data.jpa.repository.config." + "Enable" + "JpaRepositories";
        assertThat(hasAnnotation(LinkForgeApplication.class, enableJpaRepositories))
                .as("JPA repositories should not be explicitly enabled")
                .isFalse();
        String entityScan = "org.springframework.boot.autoconfigure.domain." + "Entity" + "Scan";
        assertThat(hasAnnotation(LinkForgeApplication.class, entityScan))
                .as("JPA entity scan should not be explicitly enabled")
                .isFalse();
        assertThat(AnnotationUtils.findAnnotation(MybatisConfig.class, MapperScan.class))
                .as("Task 1 bootstrap should not declare empty mapper scan packages")
                .isNull();
        assertThat(applicationContext.getBeanNamesForType(TenantAdminApplicationController.class))
                .as("Runtime bootstrap should include platform application controller")
                .isNotEmpty();
        assertThat(applicationContext.getBeanNamesForType(TenantAdminDomainController.class))
                .as("Runtime bootstrap should include platform domain controller")
                .isNotEmpty();
        assertThat(applicationContext.getBeanNamesForType(ApprovalController.class))
                .as("Runtime bootstrap should include governance approval controller")
                .isNotEmpty();
        assertThat(applicationContext.getBeanNamesForType(AuditController.class))
                .as("Runtime bootstrap should include governance audit controller")
                .isNotEmpty();
    }

    @Test
    void integrationTests_use_plain_mysql_datasource_and_primary_flyway_override() {
        var env = applicationContext.getEnvironment();

        assertThat(env.getProperty("spring.datasource.driver-class-name"))
                .isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(env.getProperty("spring.datasource.url"))
                .startsWith("jdbc:mysql:");
        assertThat(env.getProperty("spring.flyway.url"))
                .startsWith("jdbc:mysql:");
        assertThat(env.getProperty("spring.flyway.user"))
                .isEqualTo(MYSQL.getUsername());
        assertThat(env.getProperty("spring.flyway.password"))
                .isEqualTo(MYSQL.getPassword());
    }

    private static boolean hasAnnotation(Class<?> target, String annotationClassName) {
        if (target == null || annotationClassName == null || annotationClassName.isBlank()) {
            return false;
        }
        for (java.lang.annotation.Annotation ann : target.getAnnotations()) {
            if (ann == null || ann.annotationType() == null) {
                continue;
            }
            if (annotationClassName.equals(ann.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void endToEnd_register_login_create_stats_and_openapi() throws Exception {
        String email = "admin@example.com";
        String password = "password123";

        // 1) 注册
        JsonNode registerBody = objectMapper.createObjectNode()
                .put("tenantName", "demo-tenant")
                .put("email", email)
                .put("password", password);

        String registerResp = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode registerJson = objectMapper.readTree(registerResp);
        assertThat(registerJson.get("code").asInt()).isEqualTo(0);
        String token = registerJson.get("data").get("token").asText();
        long tenantId = registerJson.get("data").get("user").get("tenantId").asLong();
        assertThat(token).isNotBlank();
        assertThat(tenantId).isPositive();

        AppDomainFixture openApiFixture = provisionDedicatedApplication(
                tenantId,
                "openapi-app-" + tenantId,
                "openapi-" + tenantId + ".example.test"
        );

        // 2) 创建 API Key（用于 OpenAPI）
        JsonNode createKeyBody = objectMapper.createObjectNode()
                .put("applicationId", openApiFixture.applicationId())
                .put("name", "test-key");
        String createKeyResp = mockMvc.perform(
                        post("/api/v1/api-keys")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createKeyBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode createKeyJson = objectMapper.readTree(createKeyResp);
        assertThat(createKeyJson.get("code").asInt()).isEqualTo(0);
        String apiKey = createKeyJson.get("data").get("apiKey").asText();
        assertThat(apiKey).startsWith("lfk_");

        // 3) 创建短链（JWT）
        JsonNode createLinkBody = objectMapper.createObjectNode()
                .put("originalUrl", "https://example.com")
                .put("note", "hello");

        String createLinkResp = mockMvc.perform(
                        post("/api/v1/links")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createLinkBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode createLinkJson = objectMapper.readTree(createLinkResp);
        assertThat(createLinkJson.get("code").asInt()).isEqualTo(0);
        long linkId = createLinkJson.get("data").get("id").asLong();
        String code = createLinkJson.get("data").get("code").asText();
        String shortUrl = createLinkJson.get("data").get("shortUrl").asText();
        assertThat(linkId).isPositive();
        assertThat(code).isNotBlank();
        assertThat(shortUrl).contains("/r/" + code);

        // 4) 再创建一个短链（用于 Top 排序）
        JsonNode createLinkBody2 = objectMapper.createObjectNode()
                .put("originalUrl", "https://example.com/b")
                .put("note", "hello-b");
        String createLinkResp2 = mockMvc.perform(
                        post("/api/v1/links")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createLinkBody2))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode createLinkJson2 = objectMapper.readTree(createLinkResp2);
        assertThat(createLinkJson2.get("code").asInt()).isEqualTo(0);
        long linkId2 = createLinkJson2.get("data").get("id").asLong();
        String code2 = createLinkJson2.get("data").get("code").asText();
        assertThat(linkId2).isPositive();
        assertThat(code2).isNotBlank();

        // 5) 模拟 Redirect 统计写入（在拆分架构下由 Edge 服务写入 Redis，这里直接写入用于测试 API 侧 flush/report）
        // - code: PV 高但 UV 低
        // - code2: PV 相对低但 UV 高
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        seedStats(tenantId, linkId, today, 10, 1);
        seedStats(tenantId, linkId2, today, 5, 5);

        // 6) 手动触发一次 flush，然后查询统计
        analyticsFlushJob.flush();
        seedDimPv(tenantId, linkId, today, "referer_domain", "google.com", 3);
        analyticsDimensionFlushJob.flush();

        // 6.1) 模拟访问明细事件写入，并手动触发一次 ingest
        String streamKey = "stats:visit:events";
        redis.opsForStream().add(StreamRecords.newRecord().in(streamKey).ofStrings(java.util.Map.of(
                "ts", String.valueOf(System.currentTimeMillis()),
                "tenantId", String.valueOf(tenantId),
                "linkId", String.valueOf(linkId),
                "requestId", "rid-" + System.nanoTime(),
                "refererDomain", "google.com",
                "language", "zh-cn",
                "uaFamily", "chrome",
                "osFamily", "macos",
                "deviceType", "desktop",
                "utmSource", "ads"
        )));
        assertThat(redis.hasKey(streamKey)).isTrue();
        // ingest 为 best-effort：这里多跑几次以避免消费组初始化/IO 抖动导致的偶发空结果
        for (int i = 0; i < 3; i++) {
            analyticsEventIngestJob.ingest();
        }

        String statsResp = mockMvc.perform(
                        get("/api/v1/stats/links/" + linkId + "/daily")
                                .header("Authorization", "Bearer " + token)
                                .param("from", today.toString())
                                .param("to", today.toString())
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode statsJson = objectMapper.readTree(statsResp);
        assertThat(statsJson.get("code").asInt()).isEqualTo(0);
        assertThat(statsJson.get("data").isArray()).isTrue();
        assertThat(statsJson.get("data").size()).isGreaterThan(0);
        assertThat(statsJson.get("data").get(0).get("pv").asLong()).isGreaterThanOrEqualTo(10L);

        // 6.2) 维度分布查询（referer_domain）
        String dimResp = mockMvc.perform(
                        get("/api/v1/stats/links/" + linkId + "/dimensions")
                                .header("Authorization", "Bearer " + token)
                                .param("from", today.toString())
                                .param("to", today.toString())
                                .param("type", "referer_domain")
                                .param("limit", "10")
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode dimJson = objectMapper.readTree(dimResp);
        assertThat(dimJson.get("code").asInt()).isEqualTo(0);
        assertThat(dimJson.get("data").isArray()).isTrue();
        assertThat(dimJson.get("data").size()).isGreaterThan(0);
        assertThat(dimJson.get("data").get(0).get("value").asText()).isEqualTo("google.com");

        // 6.3) 访问明细查询
        JsonNode eventJson = null;
        for (int i = 0; i < 5; i++) {
            String eventResp = mockMvc.perform(
                            get("/api/v1/stats/links/" + linkId + "/events")
                                    .header("Authorization", "Bearer " + token)
                                    .param("limit", "10")
                    )
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            eventJson = objectMapper.readTree(eventResp);
            assertThat(eventJson.get("code").asInt()).isEqualTo(0);
            assertThat(eventJson.get("data").isArray()).isTrue();
            if (eventJson.get("data").size() > 0) {
                break;
            }
            Thread.sleep(200);
        }
        assertThat(eventJson).isNotNull();
        assertThat(eventJson.get("data").size()).isGreaterThan(0);
        assertThat(eventJson.get("data").get(0).get("requestId").asText()).isNotBlank();

        // 7) Top 链接报表（JWT）
        String topResp = mockMvc.perform(
                        get("/api/v1/stats/top-links")
                                .header("Authorization", "Bearer " + token)
                                .param("from", today.toString())
                                .param("to", today.toString())
                                .param("limit", "10")
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode topJson = objectMapper.readTree(topResp);
        assertThat(topJson.get("code").asInt()).isEqualTo(0);
        assertThat(topJson.get("data").isArray()).isTrue();
        assertThat(topJson.get("data").size()).isGreaterThanOrEqualTo(2);
        assertThat(topJson.get("data").get(0).get("code").asText()).isEqualTo(code);

        // 7.1) Top 链接报表（按 UV 排序）
        String topUvResp = mockMvc.perform(
                        get("/api/v1/stats/top-links")
                                .header("Authorization", "Bearer " + token)
                                .param("from", today.toString())
                                .param("to", today.toString())
                                .param("limit", "10")
                                .param("sortBy", "uv")
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode topUvJson = objectMapper.readTree(topUvResp);
        assertThat(topUvJson.get("code").asInt()).isEqualTo(0);
        assertThat(topUvJson.get("data").isArray()).isTrue();
        assertThat(topUvJson.get("data").size()).isGreaterThanOrEqualTo(2);
        assertThat(topUvJson.get("data").get(0).get("code").asText()).isEqualTo(code2);

        // 8) 租户隔离：再注册一个租户，确保 Top 不串租户
        JsonNode registerBody2 = objectMapper.createObjectNode()
                .put("tenantName", "demo-tenant-2")
                .put("email", "admin2@example.com")
                .put("password", password);
        String registerResp2 = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerBody2))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode registerJson2 = objectMapper.readTree(registerResp2);
        assertThat(registerJson2.get("code").asInt()).isEqualTo(0);
        String token2 = registerJson2.get("data").get("token").asText();

        // 8.1) 租户隔离：tenant2 不能访问 tenant1 的 link detail
        mockMvc.perform(
                        get("/api/v1/links/" + linkId)
                                .header("Authorization", "Bearer " + token2)
                )
                .andExpect(status().isNotFound());

        JsonNode createLinkBody3 = objectMapper.createObjectNode()
                .put("originalUrl", "https://example.com/tenant2");
        String createLinkResp3 = mockMvc.perform(
                        post("/api/v1/links")
                                .header("Authorization", "Bearer " + token2)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createLinkBody3))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode createLinkJson3 = objectMapper.readTree(createLinkResp3);
        assertThat(createLinkJson3.get("code").asInt()).isEqualTo(0);
        String code3 = createLinkJson3.get("data").get("code").asText();

        long tenantId2 = createLinkJson3.get("data").get("tenantId").asLong();
        long linkId3 = createLinkJson3.get("data").get("id").asLong();
        // This section is asserting tenant isolation in top-links, not the Redis flush pipeline again.
        // Seed the persisted daily aggregate directly so the assertion is not coupled to a second flush cycle.
        seedDailyStatsRow(tenantId2, linkId3, today, 1, 1);

        String topRespTenant1Again = mockMvc.perform(
                        get("/api/v1/stats/top-links")
                                .header("Authorization", "Bearer " + token)
                                .param("from", today.toString())
                                .param("to", today.toString())
                                .param("limit", "100")
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode topTenant1AgainJson = objectMapper.readTree(topRespTenant1Again);
        assertThat(topTenant1AgainJson.get("code").asInt()).isEqualTo(0);
        for (JsonNode row : topTenant1AgainJson.get("data")) {
            assertThat(row.get("code").asText()).isNotEqualTo(code3);
        }

        String topRespTenant2 = mockMvc.perform(
                        get("/api/v1/stats/top-links")
                                .header("Authorization", "Bearer " + token2)
                                .param("from", today.toString())
                                .param("to", today.toString())
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode topTenant2Json = objectMapper.readTree(topRespTenant2);
        assertThat(topTenant2Json.get("code").asInt()).isEqualTo(0);
        boolean foundTenant2Code = false;
        for (JsonNode row : topTenant2Json.get("data")) {
            if (code3.equals(row.get("code").asText())) {
                foundTenant2Code = true;
            }
        }
        assertThat(foundTenant2Code).isTrue();

        // 9) OpenAPI 创建短链（API Key）
        JsonNode openCreateBody = objectMapper.createObjectNode()
                .put("originalUrl", "https://example.com/openapi")
                .put("domainId", openApiFixture.domainId());
        String openCreateResp = mockMvc.perform(
                        post("/api/v1/open/links")
                                .header("X-API-Key", apiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(openCreateBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode openCreateJson = objectMapper.readTree(openCreateResp);
        assertThat(openCreateJson.get("code").asInt()).isEqualTo(0);

        // 10) RBAC：普通用户无法创建 API Key（需要管理员权限）
        var createUserBody = objectMapper.createObjectNode();
        createUserBody.put("email", "user@example.com");
        createUserBody.put("password", "password123");
        createUserBody.putArray("roles").add("USER");
        String createUserResp = mockMvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createUserBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode createUserJson = objectMapper.readTree(createUserResp);
        assertThat(createUserJson.get("code").asInt()).isEqualTo(0);

        // 登录普通用户
        JsonNode loginBody = objectMapper.createObjectNode()
                .put("email", "user@example.com")
                .put("password", "password123");
        String loginResp = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResp);
        String userToken = loginJson.get("data").get("token").asText();

        mockMvc.perform(
                        post("/api/v1/api-keys")
                                .header("Authorization", "Bearer " + userToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createKeyBody))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void register_shouldRejectDuplicateEmailAcrossTenants() throws Exception {
        String email = "dup-" + System.nanoTime() + "@example.com";
        String password = "password123";

        JsonNode registerBody1 = objectMapper.createObjectNode()
                .put("tenantName", "dup-tenant-1-" + System.nanoTime())
                .put("email", email)
                .put("password", password);
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerBody1))
                )
                .andExpect(status().isOk());

        JsonNode registerBody2 = objectMapper.createObjectNode()
                .put("tenantName", "dup-tenant-2-" + System.nanoTime())
                .put("email", email)
                .put("password", password);
        String resp2 = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerBody2))
                )
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json2 = objectMapper.readTree(resp2);
        assertThat(json2.get("code").asInt()).isEqualTo(AccountsErrorCode.EMAIL_ALREADY_EXISTS.getCode());
        assertThat(json2.get("requestId").asText()).isNotBlank();
    }

    @Test
    void createUser_shouldRejectEmailUsedInAnotherTenant() throws Exception {
        String password = "password123";

        String emailTenant1 = "t1-" + System.nanoTime() + "@example.com";
        String emailTenant2 = "t2-" + System.nanoTime() + "@example.com";

        // tenant1 注册
        JsonNode registerBody1 = objectMapper.createObjectNode()
                .put("tenantName", "t1-" + System.nanoTime())
                .put("email", emailTenant1)
                .put("password", password);
        String registerResp1 = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerBody1))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode registerJson1 = objectMapper.readTree(registerResp1);
        String token1 = registerJson1.get("data").get("token").asText();
        assertThat(token1).isNotBlank();

        // tenant2 注册
        JsonNode registerBody2 = objectMapper.createObjectNode()
                .put("tenantName", "t2-" + System.nanoTime())
                .put("email", emailTenant2)
                .put("password", password);
        String registerResp2 = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerBody2))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode registerJson2 = objectMapper.readTree(registerResp2);
        String token2 = registerJson2.get("data").get("token").asText();
        assertThat(token2).isNotBlank();

        // tenant2 尝试创建一个 email 与 tenant1 已存在用户相同的账号 -> 应失败
        JsonNode createUserBody = objectMapper.createObjectNode()
                .put("email", emailTenant1)
                .put("password", password);
        String createUserResp = mockMvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", "Bearer " + token2)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createUserBody))
                )
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode createUserJson = objectMapper.readTree(createUserResp);
        assertThat(createUserJson.get("code").asInt()).isEqualTo(AccountsErrorCode.EMAIL_ALREADY_EXISTS.getCode());
        assertThat(createUserJson.get("requestId").asText()).isNotBlank();
    }

    @Test
    void tenant_admin_should_be_able_to_manage_own_applications_but_not_other_tenants() throws Exception {
        RegisteredPrincipal tenantOne = registerTenantAdmin("control-plane-a");
        RegisteredPrincipal tenantTwo = registerTenantAdmin("control-plane-b");

        JsonNode createApplicationBody = objectMapper.createObjectNode()
                .put("applicationKey", "orders-api")
                .put("displayName", "Orders API");
        String createApplicationResp = mockMvc.perform(
                        post("/api/v1/applications")
                                .header("Authorization", "Bearer " + tenantOne.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createApplicationBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode createApplicationJson = objectMapper.readTree(createApplicationResp);
        assertThat(createApplicationJson.get("code").asInt()).isEqualTo(0);
        long applicationId = createApplicationJson.get("data").get("id").asLong();
        assertThat(applicationId).isPositive();

        JsonNode createDomainBody = objectMapper.createObjectNode()
                .put("hostname", "shared-" + tenantOne.tenantId() + ".example.test");
        String createDomainResp = mockMvc.perform(
                        post("/api/v1/domains/tenant-shared")
                                .header("Authorization", "Bearer " + tenantOne.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createDomainBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode createDomainJson = objectMapper.readTree(createDomainResp);
        assertThat(createDomainJson.get("code").asInt()).isEqualTo(0);
        long domainId = createDomainJson.get("data").get("id").asLong();
        assertThat(domainId).isPositive();

        mockMvc.perform(
                        post("/api/v1/applications/" + applicationId + "/domain-authorizations/" + domainId)
                                .header("Authorization", "Bearer " + tenantOne.token())
                )
                .andExpect(status().isOk());

        String tenantOneApplicationsResp = mockMvc.perform(
                        get("/api/v1/applications")
                                .header("Authorization", "Bearer " + tenantOne.token())
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode tenantOneApplicationsJson = objectMapper.readTree(tenantOneApplicationsResp);
        assertThat(tenantOneApplicationsJson.get("code").asInt()).isEqualTo(0);
        assertThat(tenantOneApplicationsJson.get("data").isArray()).isTrue();
        assertThat(tenantOneApplicationsJson.get("data"))
                .anySatisfy(item -> assertThat(item.get("id").asLong()).isEqualTo(applicationId));

        String tenantOneDomainsResp = mockMvc.perform(
                        get("/api/v1/domains")
                                .header("Authorization", "Bearer " + tenantOne.token())
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode tenantOneDomainsJson = objectMapper.readTree(tenantOneDomainsResp);
        assertThat(tenantOneDomainsJson.get("code").asInt()).isEqualTo(0);
        assertThat(tenantOneDomainsJson.get("data").isArray()).isTrue();
        assertThat(tenantOneDomainsJson.get("data"))
                .anySatisfy(item -> assertThat(item.get("id").asLong()).isEqualTo(domainId));

        String tenantTwoApplicationsResp = mockMvc.perform(
                        get("/api/v1/applications")
                                .header("Authorization", "Bearer " + tenantTwo.token())
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode tenantTwoApplicationsJson = objectMapper.readTree(tenantTwoApplicationsResp);
        assertThat(tenantTwoApplicationsJson.get("code").asInt()).isEqualTo(0);
        assertThat(tenantTwoApplicationsJson.get("data").isArray()).isTrue();
        assertThat(tenantTwoApplicationsJson.get("data"))
                .allSatisfy(item -> assertThat(item.get("id").asLong()).isNotEqualTo(applicationId));

        String tenantTwoDomainsResp = mockMvc.perform(
                        get("/api/v1/domains")
                                .header("Authorization", "Bearer " + tenantTwo.token())
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode tenantTwoDomainsJson = objectMapper.readTree(tenantTwoDomainsResp);
        assertThat(tenantTwoDomainsJson.get("code").asInt()).isEqualTo(0);
        assertThat(tenantTwoDomainsJson.get("data").isArray()).isTrue();
        assertThat(tenantTwoDomainsJson.get("data"))
                .allSatisfy(item -> assertThat(item.get("id").asLong()).isNotEqualTo(domainId));

        mockMvc.perform(
                        post("/api/v1/applications/" + applicationId + "/domain-authorizations/" + domainId)
                                .header("Authorization", "Bearer " + tenantTwo.token())
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void application_domain_listing_should_only_include_dedicated_and_authorized_shared_domains() throws Exception {
        RegisteredPrincipal tenant = registerTenantAdmin("app-domain-list");

        JsonNode createApplicationBody = objectMapper.createObjectNode()
                .put("applicationKey", "app-domain-list")
                .put("displayName", "Application Domain List");
        String applicationResp = mockMvc.perform(
                        post("/api/v1/applications")
                                .header("Authorization", "Bearer " + tenant.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createApplicationBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode applicationJson = objectMapper.readTree(applicationResp);
        assertThat(applicationJson.get("code").asInt()).isEqualTo(0);
        long applicationId = applicationJson.get("data").get("id").asLong();

        long authorizedSharedDomainId = createTenantSharedDomain(
                tenant.token(),
                "authorized-shared-" + tenant.tenantId() + ".example.test"
        );
        long unauthorizedSharedDomainId = createTenantSharedDomain(
                tenant.token(),
                "unauthorized-shared-" + tenant.tenantId() + ".example.test"
        );
        long dedicatedDomainId = createDedicatedDomain(
                tenant.token(),
                applicationId,
                "dedicated-" + tenant.tenantId() + ".example.test"
        );

        mockMvc.perform(
                        post("/api/v1/applications/" + applicationId + "/domain-authorizations/" + authorizedSharedDomainId)
                                .header("Authorization", "Bearer " + tenant.token())
                )
                .andExpect(status().isOk());

        String domainListResp = mockMvc.perform(
                        get("/api/v1/applications/" + applicationId + "/domains")
                                .header("Authorization", "Bearer " + tenant.token())
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode domainListJson = objectMapper.readTree(domainListResp);
        assertThat(domainListJson.get("code").asInt()).isEqualTo(0);
        assertThat(domainListJson.get("data"))
                .anySatisfy(item -> assertThat(item.get("id").asLong()).isEqualTo(dedicatedDomainId))
                .anySatisfy(item -> assertThat(item.get("id").asLong()).isEqualTo(authorizedSharedDomainId))
                .allSatisfy(item -> assertThat(item.get("id").asLong()).isNotEqualTo(unauthorizedSharedDomainId));
    }

    @Test
    void authErrors_shouldReturnConsistentApiResponse() throws Exception {
        // 1) 受保护接口：无 token -> 401 + ApiResponse(code=40100)
        String noTokenResp = mockMvc.perform(get("/api/v1/links"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode noTokenJson = objectMapper.readTree(noTokenResp);
        assertThat(noTokenJson.get("code").asInt()).isEqualTo(40100);
        assertThat(noTokenJson.get("requestId").asText()).isNotBlank();

        // 2) 受保护接口：非法 token -> 401
        String badTokenResp = mockMvc.perform(
                        get("/api/v1/links")
                                .header("Authorization", "Bearer bad-token")
                )
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode badTokenJson = objectMapper.readTree(badTokenResp);
        assertThat(badTokenJson.get("code").asInt()).isEqualTo(40100);
        assertThat(badTokenJson.get("requestId").asText()).isNotBlank();

        // 3) OpenAPI：缺少 X-API-Key -> 401 + code=40110
        JsonNode openCreateBody = objectMapper.createObjectNode()
                .put("originalUrl", "https://example.com/openapi");
        String noKeyResp = mockMvc.perform(
                        post("/api/v1/open/links")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(openCreateBody))
                )
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode noKeyJson = objectMapper.readTree(noKeyResp);
        assertThat(noKeyJson.get("code").asInt()).isEqualTo(40110);
        assertThat(noKeyJson.get("requestId").asText()).isNotBlank();

        // 4) OpenAPI：非法 X-API-Key -> 401 + code=40110
        String badKeyResp = mockMvc.perform(
                        post("/api/v1/open/links")
                                .header("X-API-Key", "lfk_1_bad")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(openCreateBody))
                )
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode badKeyJson = objectMapper.readTree(badKeyResp);
        assertThat(badKeyJson.get("code").asInt()).isEqualTo(40110);
        assertThat(badKeyJson.get("requestId").asText()).isNotBlank();
    }

    @Test
    void linkPolicyFields_should_create_update_and_echo_in_response() throws Exception {
        String email = "policy-admin@example.com";
        String password = "password123";

        JsonNode registerBody = objectMapper.createObjectNode()
                .put("tenantName", "policy-tenant")
                .put("email", email)
                .put("password", password);

        String registerResp = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode registerJson = objectMapper.readTree(registerResp);
        assertThat(registerJson.get("code").asInt()).isEqualTo(0);
        String token = registerJson.get("data").get("token").asText();
        long tenantId = registerJson.get("data").get("user").get("tenantId").asLong();
        assertThat(token).isNotBlank();

        var createLinkBody = objectMapper.createObjectNode();
        createLinkBody.put("originalUrl", "https://example.com/policy");
        createLinkBody.put("redirectStatusCode", 301);
        createLinkBody.put("previewEnabled", true);
        createLinkBody.put("unavailableLandingUrl", "https://example.com/unavailable");
        createLinkBody.put("queryForwardMode", "ALLOWLIST");
        createLinkBody.putArray("queryForwardAllowlist").add("utm_*");

        String createLinkResp = mockMvc.perform(
                        post("/api/v1/links")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createLinkBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createLinkJson = objectMapper.readTree(createLinkResp);
        assertThat(createLinkJson.get("code").asInt()).isEqualTo(0);
        JsonNode created = createLinkJson.get("data");
        long linkId = created.get("id").asLong();
        assertThat(linkId).isPositive();
        assertThat(created.get("redirectStatusCode").asInt()).isEqualTo(301);
        assertThat(created.get("previewEnabled").asBoolean()).isTrue();
        assertThat(created.get("unavailableLandingUrl").asText()).isEqualTo("https://example.com/unavailable");
        assertThat(created.get("queryForwardMode").asText()).isEqualTo("ALLOWLIST");
        assertThat(created.get("queryForwardAllowlist").isArray()).isTrue();
        assertThat(created.get("queryForwardAllowlist").size()).isEqualTo(1);
        assertThat(created.get("queryForwardAllowlist").get(0).asText()).isEqualTo("utm_*");

        var updateBody = objectMapper.createObjectNode();
        updateBody.put("clearRedirectStatusCode", true);
        updateBody.put("previewEnabled", false);
        updateBody.put("unavailableLandingUrl", "");
        updateBody.put("clearQueryForwardMode", true);
        updateBody.putArray("queryForwardAllowlist");

        String updateResp = mockMvc.perform(
                        put("/api/v1/links/" + linkId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode updateJson = objectMapper.readTree(updateResp);
        assertThat(updateJson.get("code").asInt()).isEqualTo(0);
        JsonNode updated = updateJson.get("data");
        JsonNode redirectStatusCode = updated.get("redirectStatusCode");
        assertThat(redirectStatusCode == null || redirectStatusCode.isNull()).isTrue();
        assertThat(updated.get("previewEnabled").asBoolean()).isFalse();
        JsonNode unavailableLandingUrl = updated.get("unavailableLandingUrl");
        assertThat(unavailableLandingUrl == null || unavailableLandingUrl.isNull()).isTrue();
        JsonNode queryForwardMode = updated.get("queryForwardMode");
        assertThat(queryForwardMode == null || queryForwardMode.isNull()).isTrue();
        assertThat(updated.get("queryForwardAllowlist").isArray()).isTrue();
        assertThat(updated.get("queryForwardAllowlist").size()).isEqualTo(0);
    }

    @Test
    void linkLifecycle_should_archive_restore_and_delete() throws Exception {
        String email = "lifecycle-admin@example.com";
        String password = "password123";

        JsonNode registerBody = objectMapper.createObjectNode()
                .put("tenantName", "lifecycle-tenant")
                .put("email", email)
                .put("password", password);

        String registerResp = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode registerJson = objectMapper.readTree(registerResp);
        assertThat(registerJson.get("code").asInt()).isEqualTo(0);
        String token = registerJson.get("data").get("token").asText();
        long tenantId = registerJson.get("data").get("user").get("tenantId").asLong();
        assertThat(token).isNotBlank();

        // 创建短链
        JsonNode createLinkBody = objectMapper.createObjectNode()
                .put("originalUrl", "https://example.com/lifecycle")
                .put("note", "lifecycle");
        String createResp = mockMvc.perform(
                        post("/api/v1/links")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createLinkBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode createJson = objectMapper.readTree(createResp);
        assertThat(createJson.get("code").asInt()).isEqualTo(0);
        long linkId = createJson.get("data").get("id").asLong();
        String code = createJson.get("data").get("code").asText();
        assertThat(linkId).isPositive();
        assertThat(code).isNotBlank();

        // 归档
        String archiveResp = mockMvc.perform(
                        post("/api/v1/links/" + linkId + "/archive")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode archiveJson = objectMapper.readTree(archiveResp);
        assertThat(archiveJson.get("code").asInt()).isEqualTo(0);
        JsonNode archivedAt = archiveJson.get("data").get("archivedAt");
        assertThat(archivedAt == null || archivedAt.isNull()).isFalse();

        // 活动列表应不可见
        String listActiveResp = mockMvc.perform(
                        get("/api/v1/links")
                                .header("Authorization", "Bearer " + token)
                                .param("archived", "false")
                                .param("keyword", code)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode listActiveJson = objectMapper.readTree(listActiveResp);
        assertThat(listActiveJson.get("code").asInt()).isEqualTo(0);
        assertThat(listActiveJson.get("data").get("total").asLong()).isEqualTo(0L);

        // 归档列表应可见
        String listArchivedResp = mockMvc.perform(
                        get("/api/v1/links")
                                .header("Authorization", "Bearer " + token)
                                .param("archived", "true")
                                .param("keyword", code)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode listArchivedJson = objectMapper.readTree(listArchivedResp);
        assertThat(listArchivedJson.get("code").asInt()).isEqualTo(0);
        assertThat(listArchivedJson.get("data").get("total").asLong()).isEqualTo(1L);

        // 已归档短链禁止编辑
        JsonNode updateBody = objectMapper.createObjectNode().put("note", "should-fail");
        mockMvc.perform(
                        put("/api/v1/links/" + linkId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateBody))
                )
                .andExpect(status().isBadRequest());

        // 恢复
        String restoreResp = mockMvc.perform(
                        post("/api/v1/links/" + linkId + "/restore")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode restoreJson = objectMapper.readTree(restoreResp);
        assertThat(restoreJson.get("code").asInt()).isEqualTo(0);
        JsonNode restoredArchivedAt = restoreJson.get("data").get("archivedAt");
        assertThat(restoredArchivedAt == null || restoredArchivedAt.isNull()).isTrue();

        // 未归档不允许直接删除
        mockMvc.perform(
                        delete("/api/v1/links/" + linkId)
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isBadRequest());

        // 再归档后删除
        mockMvc.perform(
                        post("/api/v1/links/" + linkId + "/archive")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        delete("/api/v1/links/" + linkId)
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk());

        // 删除后 detail 应 404
        mockMvc.perform(
                        get("/api/v1/links/" + linkId)
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void user_and_api_key_governance_should_disable_enable_rotate_and_reset_password() throws Exception {
        String email = "govern-admin@example.com";
        String password = "password123";

        JsonNode registerBody = objectMapper.createObjectNode()
                .put("tenantName", "govern-tenant")
                .put("email", email)
                .put("password", password);

        String registerResp = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode registerJson = objectMapper.readTree(registerResp);
        assertThat(registerJson.get("code").asInt()).isEqualTo(0);
        String token = registerJson.get("data").get("token").asText();
        long tenantId = registerJson.get("data").get("user").get("tenantId").asLong();
        assertThat(token).isNotBlank();

        AppDomainFixture openApiFixture = provisionDedicatedApplication(
                tenantId,
                "govern-openapi-app-" + tenantId,
                "govern-openapi-" + tenantId + ".example.test"
        );

        // 1) API Key：disable/enable/rotate + OpenAPI 验证
        JsonNode createKeyBody = objectMapper.createObjectNode()
                .put("applicationId", openApiFixture.applicationId())
                .put("name", "govern-key");
        String createKeyResp = mockMvc.perform(
                        post("/api/v1/api-keys")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createKeyBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode createKeyJson = objectMapper.readTree(createKeyResp);
        assertThat(createKeyJson.get("code").asInt()).isEqualTo(0);
        long apiKeyId = createKeyJson.get("data").get("id").asLong();
        String apiKey = createKeyJson.get("data").get("apiKey").asText();
        assertThat(apiKeyId).isPositive();
        assertThat(apiKey).isNotBlank();

        String disableKeyResp = mockMvc.perform(
                        put("/api/v1/api-keys/" + apiKeyId + "/disable")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode disableKeyJson = objectMapper.readTree(disableKeyResp);
        assertThat(disableKeyJson.get("code").asInt()).isEqualTo(0);
        assertThat(disableKeyJson.get("data").get("status").asText()).isEqualTo("disabled");

        JsonNode openCreateBody = objectMapper.createObjectNode()
                .put("originalUrl", "https://example.com/open-1")
                .put("domainId", openApiFixture.domainId());
        String openDisabledResp = mockMvc.perform(
                        post("/api/v1/open/links")
                                .header("X-API-Key", apiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(openCreateBody))
                )
                .andExpect(status().isForbidden())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode openDisabledJson = objectMapper.readTree(openDisabledResp);
        assertThat(openDisabledJson.get("code").asInt()).isEqualTo(OpenApiErrorCode.API_KEY_DISABLED.getCode());

        mockMvc.perform(
                        put("/api/v1/api-keys/" + apiKeyId + "/enable")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/v1/open/links")
                                .header("X-API-Key", apiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(openCreateBody))
                )
                .andExpect(status().isOk());

        String rotateResp = mockMvc.perform(
                        post("/api/v1/api-keys/" + apiKeyId + "/rotate")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode rotateJson = objectMapper.readTree(rotateResp);
        assertThat(rotateJson.get("code").asInt()).isEqualTo(0);
        String newApiKey = rotateJson.get("data").get("apiKey").asText();
        assertThat(newApiKey).isNotBlank();
        assertThat(newApiKey).isNotEqualTo(apiKey);

        // rotate 后旧 key 应失效
        String openOldKeyResp = mockMvc.perform(
                        post("/api/v1/open/links")
                                .header("X-API-Key", apiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(openCreateBody))
                )
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode openOldKeyJson = objectMapper.readTree(openOldKeyResp);
        assertThat(openOldKeyJson.get("code").asInt()).isEqualTo(OpenApiErrorCode.API_KEY_INVALID.getCode());

        mockMvc.perform(
                        post("/api/v1/open/links")
                                .header("X-API-Key", newApiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(openCreateBody))
                )
                .andExpect(status().isOk());

        // 2) 用户：disable/enable/reset password + 登录验证
        String userEmail = "user-govern@example.com";
        String userPassword = "password123";
        JsonNode createUserBody = objectMapper.createObjectNode()
                .put("email", userEmail)
                .put("password", userPassword);

        String createUserResp = mockMvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createUserBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode createUserJson = objectMapper.readTree(createUserResp);
        assertThat(createUserJson.get("code").asInt()).isEqualTo(0);
        long userId = createUserJson.get("data").get("id").asLong();
        assertThat(userId).isPositive();

        // 初始可登录
        JsonNode loginBody = objectMapper.createObjectNode()
                .put("email", userEmail)
                .put("password", userPassword);
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginBody))
                )
                .andExpect(status().isOk());

        // 禁用后不可登录，且与未知账号/错误密码使用同一响应，避免账号枚举。
        mockMvc.perform(
                        put("/api/v1/users/" + userId + "/disable")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk());

        String loginDisabledResp = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginBody))
                )
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode loginDisabledJson = objectMapper.readTree(loginDisabledResp);
        assertThat(loginDisabledJson.get("code").asInt()).isEqualTo(AccountsErrorCode.INVALID_CREDENTIALS.getCode());

        // 启用 + 重置密码
        mockMvc.perform(
                        put("/api/v1/users/" + userId + "/enable")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk());

        String newPassword = "newpassword123";
        JsonNode resetPwdBody = objectMapper.createObjectNode().put("password", newPassword);
        mockMvc.perform(
                        put("/api/v1/users/" + userId + "/password")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(resetPwdBody))
                )
                .andExpect(status().isOk());

        // 旧密码应失败
        String loginOldPwdResp = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginBody))
                )
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode loginOldPwdJson = objectMapper.readTree(loginOldPwdResp);
        assertThat(loginOldPwdJson.get("code").asInt()).isEqualTo(AccountsErrorCode.INVALID_CREDENTIALS.getCode());

        // 新密码可登录
        JsonNode loginNewPwdBody = objectMapper.createObjectNode()
                .put("email", userEmail)
                .put("password", newPassword);
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginNewPwdBody))
                )
                .andExpect(status().isOk());
    }

    private void seedStats(long tenantId, long linkId, LocalDate day, long pv, long uv) {
        String dayRaw = day.format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        String pvKey = "stats:pv:" + tenantId + ":" + linkId + ":" + dayRaw;
        String uvKey = "stats:uv:" + tenantId + ":" + linkId + ":" + dayRaw;
        String statsDirtyStreamKey = "stats:dirty:flush:" + dayRaw;
        String dimDirtyStreamKey = "stats:dirty:dim:" + dayRaw;
        String dirtyMember = tenantId + ":" + linkId;

        for (int i = 0; i < pv; i++) {
            redis.opsForValue().increment(pvKey);
        }
        for (int i = 0; i < uv; i++) {
            redis.opsForHyperLogLog().add(uvKey, "v" + i + "-" + tenantId + "-" + linkId);
        }
        redis.opsForStream().add(StreamRecords.newRecord().in(statsDirtyStreamKey).ofStrings(java.util.Map.of(
                "member", dirtyMember,
                "ts", String.valueOf(System.currentTimeMillis())
        )));
        redis.opsForStream().add(StreamRecords.newRecord().in(dimDirtyStreamKey).ofStrings(java.util.Map.of(
                "member", dirtyMember,
                "ts", String.valueOf(System.currentTimeMillis())
        )));
    }

    private AppDomainFixture provisionDedicatedApplication(long tenantId, String applicationKey, String hostname) {
        long applicationId = Math.abs(System.nanoTime()) + 10_000;
        long domainId = applicationId + 1_000;
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

    private void seedDailyStatsRow(long tenantId, long linkId, LocalDate day, long pv, long uv) {
        jdbcTemplate.update(
                """
                        INSERT INTO link_stats_daily (link_id, tenant_id, day, pv, uv, updated_at)
                        VALUES (?, ?, ?, ?, ?, NOW())
                        ON DUPLICATE KEY UPDATE
                            pv = VALUES(pv),
                            uv = VALUES(uv),
                            updated_at = NOW()
                        """,
                linkId,
                tenantId,
                day,
                pv,
                uv
        );
    }

    private void seedDimPv(long tenantId, long linkId, LocalDate day, String dimType, String dimValue, long pv) {
        String dayRaw = day.format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        String t = dimType == null ? "unknown" : dimType.trim().toLowerCase();
        if (t.isBlank()) {
            t = "unknown";
        }
        t = t.replace(':', '_');
        String key = "stats:dim:pv:" + tenantId + ":" + linkId + ":" + dayRaw + ":" + t;

        for (int i = 0; i < pv; i++) {
            redis.opsForHash().increment(key, dimValue, 1L);
        }
    }

    private long createTenantSharedDomain(String token, String hostname) throws Exception {
        JsonNode createDomainBody = objectMapper.createObjectNode().put("hostname", hostname);
        String response = mockMvc.perform(
                        post("/api/v1/domains/tenant-shared")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createDomainBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("code").asInt()).isEqualTo(0);
        return json.get("data").get("id").asLong();
    }

    private long createDedicatedDomain(String token, long applicationId, String hostname) throws Exception {
        JsonNode createDomainBody = objectMapper.createObjectNode().put("hostname", hostname);
        String response = mockMvc.perform(
                        post("/api/v1/applications/" + applicationId + "/domains")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createDomainBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("code").asInt()).isEqualTo(0);
        return json.get("data").get("id").asLong();
    }

    private RegisteredPrincipal registerTenantAdmin(String tenantNamePrefix) throws Exception {
        String suffix = Long.toUnsignedString(System.nanoTime());
        JsonNode registerBody = objectMapper.createObjectNode()
                .put("tenantName", tenantNamePrefix + "-" + suffix)
                .put("email", tenantNamePrefix + "-" + suffix + "@example.com")
                .put("password", "password123");
        String registerResp = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerBody))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode registerJson = objectMapper.readTree(registerResp);
        return new RegisteredPrincipal(
                registerJson.get("data").get("token").asText(),
                registerJson.get("data").get("user").get("tenantId").asLong()
        );
    }

    private record AppDomainFixture(long applicationId, long domainId) {
    }

    private record RegisteredPrincipal(String token, long tenantId) {
    }
}
