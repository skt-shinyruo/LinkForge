package com.linkforge.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.LinkForgeApplication;
import com.linkforge.analytics.infrastructure.job.AnalyticsFlushJob;
import com.linkforge.analytics.infrastructure.job.AnalyticsRedirectEventProjectorJob;
import com.linkforge.analytics.infrastructure.catalog.ShortLinkCatalogProjectorJob;
import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.testsupport.SharedIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.scheduling.enabled=false"
)
@AutoConfigureMockMvc
class ApplicationScopedStatsIntegrationTest extends SharedIntegrationTestSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        registerDisabledAnalytics(r);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    AnalyticsFlushJob analyticsFlushJob;

    @Autowired
    AnalyticsRedirectEventProjectorJob analyticsRedirectEventProjectorJob;

    @Autowired
    ShortLinkCatalogProjectorJob shortLinkCatalogProjectorJob;

    @Test
    void stats_should_be_queryable_at_application_scope_without_leaking_other_applications() throws Exception {
        RegisteredPrincipal principal = registerTenantAdmin();
        AppDomainFixture appOneDomainOne = provisionDedicatedApplication(principal.tenantId(), "stats-app-one", "stats-app-one-a.example.test");
        long appOneDomainTwo = provisionAdditionalDomain(principal.tenantId(), appOneDomainOne.applicationId(), "stats-app-one-b.example.test");
        AppDomainFixture appTwoDomainOne = provisionDedicatedApplication(principal.tenantId(), "stats-app-two", "stats-app-two-a.example.test");

        JsonNode appOneLinkOne = createLink(principal.token(), appOneDomainOne.applicationId(), appOneDomainOne.domainId(), "https://example.com/a1");
        JsonNode appOneLinkTwo = createLink(principal.token(), appOneDomainOne.applicationId(), appOneDomainTwo, "https://example.com/a2");
        JsonNode appTwoLink = createLink(principal.token(), appTwoDomainOne.applicationId(), appTwoDomainOne.domainId(), "https://example.com/b1");

        long linkOneId = appOneLinkOne.get("data").get("id").asLong();
        long linkTwoId = appOneLinkTwo.get("data").get("id").asLong();
        long otherLinkId = appTwoLink.get("data").get("id").asLong();
        String linkOneCode = appOneLinkOne.get("data").get("code").asText();
        String linkTwoCode = appOneLinkTwo.get("data").get("code").asText();
        String otherLinkCode = appTwoLink.get("data").get("code").asText();

        shortLinkCatalogProjectorJob.project();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        seedStats(principal.tenantId(), linkOneId, today, 10, 2);
        seedStats(principal.tenantId(), linkTwoId, today, 4, 4);
        seedStats(principal.tenantId(), otherLinkId, today, 99, 9);
        analyticsFlushJob.flush();

        JsonNode appOverview = getJson(
                get("/api/v1/stats/applications/" + appOneDomainOne.applicationId() + "/overview")
                        .header("Authorization", "Bearer " + principal.token())
                        .param("from", today.toString())
                        .param("to", today.toString())
        );
        assertThat(appOverview.get("code").asInt()).isEqualTo(0);
        assertThat(appOverview.get("data")).hasSize(1);
        assertThat(appOverview.get("data").get(0).get("pv").asLong()).isEqualTo(14L);
        assertThat(appOverview.get("data").get(0).get("uv").asLong()).isEqualTo(6L);

        JsonNode appTopLinks = getJson(
                get("/api/v1/stats/applications/" + appOneDomainOne.applicationId() + "/top-links")
                        .header("Authorization", "Bearer " + principal.token())
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .param("limit", "10")
        );
        assertThat(appTopLinks.get("code").asInt()).isEqualTo(0);
        assertThat(appTopLinks.get("data")).hasSize(2);
        assertThat(appTopLinks.get("data").toString()).contains(linkOneCode, linkTwoCode);
        assertThat(appTopLinks.get("data").toString()).doesNotContain(otherLinkCode);

        JsonNode domainOverview = getJson(
                get("/api/v1/stats/domains/" + appOneDomainOne.domainId() + "/overview")
                        .header("Authorization", "Bearer " + principal.token())
                        .param("from", today.toString())
                        .param("to", today.toString())
        );
        assertThat(domainOverview.get("code").asInt()).isEqualTo(0);
        assertThat(domainOverview.get("data")).hasSize(1);
        assertThat(domainOverview.get("data").get(0).get("pv").asLong()).isEqualTo(10L);
        assertThat(domainOverview.get("data").get(0).get("uv").asLong()).isEqualTo(2L);

        archiveLink(principal.token(), linkOneId);
        deleteLink(principal.token(), linkOneId);
        shortLinkCatalogProjectorJob.project();

        JsonNode appOverviewAfterDelete = getJson(
                get("/api/v1/stats/applications/" + appOneDomainOne.applicationId() + "/overview")
                        .header("Authorization", "Bearer " + principal.token())
                        .param("from", today.toString())
                        .param("to", today.toString())
        );
        assertThat(appOverviewAfterDelete.get("data")).hasSize(1);
        assertThat(appOverviewAfterDelete.get("data").get(0).get("pv").asLong()).isEqualTo(14L);
        assertThat(appOverviewAfterDelete.get("data").get(0).get("uv").asLong()).isEqualTo(6L);

        JsonNode domainOverviewAfterDelete = getJson(
                get("/api/v1/stats/domains/" + appOneDomainOne.domainId() + "/overview")
                        .header("Authorization", "Bearer " + principal.token())
                        .param("from", today.toString())
                        .param("to", today.toString())
        );
        assertThat(domainOverviewAfterDelete.get("data")).hasSize(1);
        assertThat(domainOverviewAfterDelete.get("data").get(0).get("pv").asLong()).isEqualTo(10L);
        assertThat(domainOverviewAfterDelete.get("data").get(0).get("uv").asLong()).isEqualTo(2L);

        JsonNode appTopLinksAfterDelete = getJson(
                get("/api/v1/stats/applications/" + appOneDomainOne.applicationId() + "/top-links")
                        .header("Authorization", "Bearer " + principal.token())
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .param("limit", "10")
        );
        assertThat(appTopLinksAfterDelete.get("data")).hasSize(2);
        assertThat(appTopLinksAfterDelete.get("data").toString()).contains(linkOneCode, linkTwoCode);
        assertThat(appTopLinksAfterDelete.get("data").toString()).doesNotContain(otherLinkCode);
        assertThat(appTopLinksAfterDelete.get("data")).anySatisfy(row -> {
            assertThat(row.get("linkId").asLong()).isEqualTo(linkOneId);
            assertThat(row.get("deleted").asBoolean()).isTrue();
        });
    }

    @Test
    void scopeOverviewUv_shouldDeduplicateVisitorsAcrossLinks() throws Exception {
        RegisteredPrincipal principal = registerTenantAdmin();
        AppDomainFixture fixture = provisionDedicatedApplication(principal.tenantId(), "stats-dedup-app", "stats-dedup.example.test");

        JsonNode firstLink = createLink(principal.token(), fixture.applicationId(), fixture.domainId(), "https://example.com/dedup-a");
        JsonNode secondLink = createLink(principal.token(), fixture.applicationId(), fixture.domainId(), "https://example.com/dedup-b");
        long firstLinkId = firstLink.get("data").get("id").asLong();
        long secondLinkId = secondLink.get("data").get("id").asLong();

        shortLinkCatalogProjectorJob.project();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        long ts = today.atStartOfDay(ZoneOffset.UTC).plusHours(9).toInstant().toEpochMilli();
        seedVisitEvent(principal.tenantId(), fixture.applicationId(), fixture.domainId(), firstLinkId, ts, "same-visitor");
        seedVisitEvent(principal.tenantId(), fixture.applicationId(), fixture.domainId(), secondLinkId, ts, "same-visitor");
        analyticsRedirectEventProjectorJob.project();
        analyticsFlushJob.flush();

        JsonNode tenantOverview = getJson(
                get("/api/v1/stats/overview")
                        .header("Authorization", "Bearer " + principal.token())
                        .param("from", today.toString())
                        .param("to", today.toString())
        );
        assertThat(tenantOverview.get("data")).hasSize(1);
        assertThat(tenantOverview.get("data").get(0).get("pv").asLong()).isEqualTo(2L);
        assertThat(tenantOverview.get("data").get(0).get("uv").asLong()).isEqualTo(1L);

        JsonNode appOverview = getJson(
                get("/api/v1/stats/applications/" + fixture.applicationId() + "/overview")
                        .header("Authorization", "Bearer " + principal.token())
                        .param("from", today.toString())
                        .param("to", today.toString())
        );
        assertThat(appOverview.get("data")).hasSize(1);
        assertThat(appOverview.get("data").get(0).get("pv").asLong()).isEqualTo(2L);
        assertThat(appOverview.get("data").get(0).get("uv").asLong()).isEqualTo(1L);

        JsonNode domainOverview = getJson(
                get("/api/v1/stats/domains/" + fixture.domainId() + "/overview")
                        .header("Authorization", "Bearer " + principal.token())
                        .param("from", today.toString())
                        .param("to", today.toString())
        );
        assertThat(domainOverview.get("data")).hasSize(1);
        assertThat(domainOverview.get("data").get(0).get("pv").asLong()).isEqualTo(2L);
        assertThat(domainOverview.get("data").get(0).get("uv").asLong()).isEqualTo(1L);
    }

    @Test
    void detailed_export_should_require_governance_request_before_download() throws Exception {
        RegisteredPrincipal principal = registerTenantAdmin();
        AppDomainFixture fixture = provisionDedicatedApplication(principal.tenantId(), "stats-export-app", "stats-export.example.test");
        JsonNode link = createLink(principal.token(), fixture.applicationId(), fixture.domainId(), "https://example.com/export");
        long linkId = link.get("data").get("id").asLong();

        LocalDateTime to = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime from = to.minusDays(1);

        String response = mockMvc.perform(
                        post("/api/v1/stats/links/" + linkId + "/events/export-requests")
                                .header("Authorization", "Bearer " + principal.token())
                                .param("from", from.toString())
                                .param("to", to.toString())
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("code").asInt()).isEqualTo(0);
        assertThat(json.get("data").get("operation").asText()).isEqualTo("ANALYTICS_DETAIL_EXPORT");
        assertThat(json.get("data").get("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(json.get("data").get("targetApplicationId").asLong()).isEqualTo(fixture.applicationId());

        Integer approvalCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM approval_requests
                        WHERE tenant_id = ?
                          AND operation_type = 'ANALYTICS_DETAIL_EXPORT'
                        """,
                Integer.class,
                principal.tenantId()
        );
        assertThat(approvalCount).isEqualTo(1);
    }

    private RegisteredPrincipal registerTenantAdmin() throws Exception {
        String suffix = Long.toUnsignedString(System.nanoTime());
        JsonNode registerBody = objectMapper.createObjectNode()
                .put("tenantName", "analytics-tenant-" + suffix)
                .put("email", "analytics-admin-" + suffix + "@example.com")
                .put("password", "password123");
        JsonNode register = getJson(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerBody))
        );
        return new RegisteredPrincipal(
                register.get("data").get("token").asText(),
                register.get("data").get("user").get("tenantId").asLong()
        );
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

    private long provisionAdditionalDomain(long tenantId, long applicationId, String hostname) {
        long domainId = Math.abs(System.nanoTime()) + 20_000;
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
        return domainId;
    }

    private JsonNode createLink(String token, long applicationId, long domainId, String originalUrl) throws Exception {
        String suffix = Long.toUnsignedString(System.nanoTime());
        JsonNode createLinkBody = objectMapper.createObjectNode()
                .put("originalUrl", originalUrl)
                .put("customCode", "stats" + suffix)
                .put("applicationId", applicationId)
                .put("domainId", domainId);
        return getJson(
                post("/api/v1/links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLinkBody))
        );
    }

    private void archiveLink(String token, long linkId) throws Exception {
        getJson(
                post("/api/v1/links/" + linkId + "/archive")
                        .header("Authorization", "Bearer " + token)
        );
    }

    private void deleteLink(String token, long linkId) throws Exception {
        getJson(
                delete("/api/v1/links/" + linkId)
                        .header("Authorization", "Bearer " + token)
        );
    }

    private void seedStats(long tenantId, long linkId, LocalDate day, long pv, long uv) {
        String pvKey = AnalyticsKeys.pvKey(tenantId, linkId, day);
        String uvKey = AnalyticsKeys.uvKey(tenantId, linkId, day);
        String statsDirtyStreamKey = AnalyticsKeys.statsDirtyStreamKey(day);
        String dirtyMember = AnalyticsKeys.dirtyLinkMember(tenantId, linkId);

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
    }

    private void seedVisitEvent(long tenantId, long applicationId, long domainId, long linkId, long ts, String visitorKey) {
        redis.opsForStream().add(StreamRecords.newRecord().in(AnalyticsKeys.visitEventStreamKey()).ofStrings(java.util.Map.of(
                "ts", String.valueOf(ts),
                "tenantId", String.valueOf(tenantId),
                "applicationId", String.valueOf(applicationId),
                "domainId", String.valueOf(domainId),
                "linkId", String.valueOf(linkId),
                "visitorKey", visitorKey
        )));
    }

    private JsonNode getJson(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        String content = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(content);
        assertThat(json.get("code").asInt()).isEqualTo(0);
        return json;
    }

    private record RegisteredPrincipal(String token, long tenantId) {
    }

    private record AppDomainFixture(long applicationId, long domainId) {
    }
}
