package com.linkforge;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.shortlink.infrastructure.query.MybatisShortLinkReadRepository;
import com.linkforge.testsupport.SharedIntegrationTestSupport;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.scheduling.enabled=false"
)
@AutoConfigureMockMvc
class RedirectNegativeCacheIntegrationTest extends SharedIntegrationTestSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        // 避免启动期严格校验失败（以及减少 log 噪音）
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
    MybatisShortLinkReadRepository shortLinkReadRepository;

    @BeforeEach
    void setUp() {
        // 避免测试间缓存 key 干扰
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        clearInvocations(shortLinkReadRepository);
    }

    @Test
    void not_found_should_be_negative_cached_to_reduce_db_lookups() throws Exception {
        String code = "missing" + Long.toUnsignedString(System.nanoTime());

        mockMvc.perform(get("/r/" + code).header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/r/" + code).header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNotFound());

        verify(shortLinkReadRepository, times(1)).findRedirectMetaByHostAndCode("localhost", code);
    }

    @Test
    void cache_evict_should_trigger_shortlink_read_through_refill() throws Exception {
        long tenantId = 104L;
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, tenantId);

        long suffix = System.nanoTime();
        String code = "refill" + Long.toHexString(suffix);
        String host = "refill-" + suffix + ".example.test";
        String originalUrl = "https://example.com/refill/" + suffix;

        long applicationId = suffix + 11;
        long domainId = suffix + 21;
        long linkId = suffix + 31;

        insertApplication(applicationId, tenantId, "app-" + suffix, "App " + suffix);
        insertDedicatedDomain(domainId, tenantId, applicationId, host);
        insertShortLink(linkId, tenantId, applicationId, domainId, code, originalUrl);

        mockMvc.perform(get("/r/" + code)
                        .with(host(host))
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, originalUrl));

        mockMvc.perform(get("/r/" + code)
                        .with(host(host))
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, originalUrl));

        redis.getConnectionFactory().getConnection().serverCommands().flushAll();

        mockMvc.perform(get("/r/" + code)
                        .with(host(host))
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, originalUrl));

        mockMvc.perform(get("/r/" + code)
                        .with(host(host))
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, originalUrl));

        verify(shortLinkReadRepository, times(2)).findRedirectMetaByHostAndCode(host, code);
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
