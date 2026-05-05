package com.linkforge.platform;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.shortlink.application.migration.BackfillResult;
import com.linkforge.shortlink.application.migration.LegacyShortLinkBackfillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class LegacyShortLinkMigrationIntegrationTest extends PlatformPersistenceIntegrationTestSupport {

    private static final long TENANT_ID = 81003L;
    private static final long LINK_ID = 91003L;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    LegacyShortLinkBackfillService backfillService;

    @BeforeEach
    void setUpTenantAndLegacyRow() {
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, TENANT_ID);
        jdbcTemplate.update("DELETE FROM short_links WHERE id = ?", LINK_ID);
        jdbcTemplate.update("DELETE FROM application_domain_authorizations WHERE application_id IN (SELECT id FROM applications WHERE tenant_id = ?)", TENANT_ID);
        jdbcTemplate.update("DELETE FROM application_policies WHERE application_id IN (SELECT id FROM applications WHERE tenant_id = ?)", TENANT_ID);
        jdbcTemplate.update("DELETE FROM application_quotas WHERE application_id IN (SELECT id FROM applications WHERE tenant_id = ?)", TENANT_ID);
        jdbcTemplate.update("DELETE FROM domains WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM applications WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update(
                """
                        INSERT INTO short_links (
                            id, tenant_id, application_id, domain_id, code, lifecycle_state, original_url, note,
                            enabled, expires_at, archived_at, redirect_status_code, preview_enabled,
                            unavailable_landing_url, query_forward_mode, query_forward_allowlist,
                            created_by_type, created_by, version
                        ) VALUES (?, ?, NULL, NULL, ?, 'ACTIVE', ?, ?, b'1', NULL, NULL, NULL, b'0', NULL, NULL, NULL, 'USER', ?, 0)
                        """,
                LINK_ID,
                TENANT_ID,
                "legacyCode" + TENANT_ID,
                "https://example.com/legacy",
                "legacy",
                1001L
        );
    }

    @Test
    void backfill_should_place_existing_tenant_links_into_default_application_and_legacy_domain_idempotently() {
        BackfillResult first = backfillService.backfillTenant(TENANT_ID);
        BackfillResult second = backfillService.backfillTenant(TENANT_ID);

        assertThat(first.updatedCount()).isEqualTo(1);
        assertThat(second.updatedCount()).isZero();
        assertThat(first.applicationId()).isEqualTo(second.applicationId());
        assertThat(first.domainId()).isEqualTo(second.domainId());

        Integer applicationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE tenant_id = ?",
                Integer.class,
                TENANT_ID
        );
        assertThat(applicationCount).isEqualTo(1);

        Long persistedTenantId = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM applications WHERE id = ?",
                Long.class,
                first.applicationId()
        );
        assertThat(persistedTenantId).isEqualTo(TENANT_ID);

        Long applicationId = jdbcTemplate.queryForObject(
                "SELECT application_id FROM short_links WHERE id = ?",
                Long.class,
                LINK_ID
        );
        Long domainId = jdbcTemplate.queryForObject(
                "SELECT domain_id FROM short_links WHERE id = ?",
                Long.class,
                LINK_ID
        );
        assertThat(applicationId).isEqualTo(first.applicationId());
        assertThat(domainId).isEqualTo(first.domainId());

        String scope = jdbcTemplate.queryForObject("SELECT scope FROM domains WHERE id = ?", String.class, domainId);
        assertThat(scope).isEqualTo("APPLICATION_DEDICATED");
    }
}
