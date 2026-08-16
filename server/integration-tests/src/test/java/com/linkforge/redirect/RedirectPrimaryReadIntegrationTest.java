package com.linkforge.redirect;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
import com.linkforge.testsupport.SharedReadWriteIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.scheduling.enabled=false"
)
@AutoConfigureMockMvc
class RedirectPrimaryReadIntegrationTest extends SharedReadWriteIntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ShortLinkReadPort shortLinkReadPort;

    @Test
    void newly_created_link_should_redirect_from_primary_while_replica_is_stale() throws Exception {
        long suffix = System.nanoTime();
        long tenantId = suffix + 11L;
        String code = "fresh" + Long.toUnsignedString(suffix, 36);
        String originalUrl = "https://example.com/fresh/" + suffix;

        JdbcTemplate primary = jdbc(PRIMARY);
        TestTenantFixtures.ensureTenantExists(primary, tenantId);
        insertUnscopedShortLink(primary, suffix + 21L, tenantId, code, originalUrl);

        mockMvc.perform(get("/r/" + code)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, originalUrl));
    }

    @Test
    void updated_link_should_not_cache_stale_replica_metadata() throws Exception {
        long suffix = System.nanoTime();
        long tenantId = suffix + 31L;
        long linkId = suffix + 41L;
        String code = "updated" + Long.toUnsignedString(suffix, 36);
        String staleUrl = "https://example.com/stale/" + suffix;
        String currentUrl = "https://example.com/current/" + suffix;

        JdbcTemplate primary = jdbc(PRIMARY);
        JdbcTemplate replica = jdbc(REPLICA);
        TestTenantFixtures.ensureTenantExists(primary, tenantId);
        TestTenantFixtures.ensureTenantExists(replica, tenantId);
        insertUnscopedShortLink(primary, linkId, tenantId, code, currentUrl);
        insertUnscopedShortLink(replica, linkId, tenantId, code, staleUrl);

        assertRedirectsTo(code, currentUrl);
        assertRedirectsTo(code, currentUrl);
    }

    @Test
    void hostless_authoritative_lookup_should_not_match_scoped_link() {
        long suffix = System.nanoTime();
        long tenantId = suffix + 51L;
        long applicationId = suffix + 61L;
        long domainId = suffix + 71L;
        String code = "scoped" + Long.toUnsignedString(suffix, 36);

        JdbcTemplate primary = jdbc(PRIMARY);
        TestTenantFixtures.ensureTenantExists(primary, tenantId);
        insertApplication(primary, applicationId, tenantId, "app-" + suffix);
        insertDomain(primary, domainId, tenantId, applicationId, "scoped-" + suffix + ".example.test");
        insertScopedShortLink(
                primary,
                suffix + 81L,
                tenantId,
                applicationId,
                domainId,
                code,
                "https://example.com/scoped/" + suffix
        );

        assertThat(shortLinkReadPort.findRedirectMetaByHostAndCode(null, code)).isEmpty();
    }

    private static JdbcTemplate jdbc(MySQLContainer<?> mysql) {
        return new JdbcTemplate(new DriverManagerDataSource(
                mysql.getJdbcUrl(),
                mysql.getUsername(),
                mysql.getPassword()
        ));
    }

    private void assertRedirectsTo(String code, String originalUrl) throws Exception {
        mockMvc.perform(get("/r/" + code)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, originalUrl));
    }

    private static void insertApplication(
            JdbcTemplate jdbc,
            long applicationId,
            long tenantId,
            String applicationKey
    ) {
        jdbc.update(
                """
                        INSERT INTO applications (id, tenant_id, application_key, display_name, status)
                        VALUES (?, ?, ?, ?, 'ACTIVE')
                        """,
                applicationId,
                tenantId,
                applicationKey,
                "Application " + applicationId
        );
    }

    private static void insertDomain(
            JdbcTemplate jdbc,
            long domainId,
            long tenantId,
            long applicationId,
            String hostname
    ) {
        jdbc.update(
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

    private static void insertScopedShortLink(
            JdbcTemplate jdbc,
            long linkId,
            long tenantId,
            long applicationId,
            long domainId,
            String code,
            String originalUrl
    ) {
        jdbc.update(
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

    private static void insertUnscopedShortLink(
            JdbcTemplate jdbc,
            long linkId,
            long tenantId,
            String code,
            String originalUrl
    ) {
        jdbc.update(
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
                        ) VALUES (?, ?, NULL, NULL, ?, 'ACTIVE', ?, NULL, 1, NULL, NULL, 302, 0, NULL, NULL, NULL, 'USER', 1, 0)
                        """,
                linkId,
                tenantId,
                code,
                originalUrl
        );
    }
}
