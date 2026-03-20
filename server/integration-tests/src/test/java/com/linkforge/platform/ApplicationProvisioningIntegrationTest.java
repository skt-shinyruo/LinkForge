package com.linkforge.platform;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.platform.application.ApplicationProvisioningService;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.shortlink.application.ShortLinkService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ApplicationProvisioningIntegrationTest extends PlatformPersistenceIntegrationTestSupport {

    private static final long TENANT_ID = 81001L;

    @Autowired
    ApplicationProvisioningService provisioningService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ShortLinkService shortLinkService;

    @BeforeEach
    void setUpTenantAndAuth() {
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, TENANT_ID);
        authenticateAsTenantAdmin(TENANT_ID);
    }

    @Test
    void createApplication_should_persist_tenant_scoped_application_with_default_policy() {
        ApplicationProvisioningService.ApplicationDto created = provisioningService.createApplication(
                TENANT_ID,
                new ApplicationProvisioningService.CreateApplicationRequest("control-plane", "Internal control plane")
        );

        Map<String, Object> applicationRow = jdbcTemplate.queryForMap(
                """
                        SELECT id, tenant_id, application_key, display_name
                        FROM applications
                        WHERE id = ?
                        """,
                created.id()
        );
        assertThat(applicationRow)
                .containsEntry("id", created.id())
                .containsEntry("tenant_id", created.tenantId())
                .containsEntry("application_key", "control-plane")
                .containsEntry("display_name", "Internal control plane");

        Map<String, Object> policyRow = jdbcTemplate.queryForMap(
                """
                        SELECT application_id, default_domain_scope, default_redirect_status_code, preview_enabled
                        FROM application_policies
                        WHERE application_id = ?
                        """,
                created.id()
        );
        assertThat(policyRow)
                .containsEntry("application_id", created.id())
                .containsEntry("default_domain_scope", "TENANT_SHARED")
                .containsEntry("default_redirect_status_code", 302);
        assertThat(((Number) policyRow.get("preview_enabled")).intValue()).isZero();

        Map<String, Object> quotaRow = jdbcTemplate.queryForMap(
                """
                        SELECT application_id, monthly_link_limit, monthly_click_limit
                        FROM application_quotas
                        WHERE application_id = ?
                        """,
                created.id()
        );
        assertThat(quotaRow)
                .containsEntry("application_id", created.id())
                .containsEntry("monthly_link_limit", 10000)
                .containsEntry("monthly_click_limit", 1000000L);
    }

    @Test
    void link_create_should_fail_when_application_quota_is_exceeded() {
        ApplicationProvisioningService.ApplicationDto app = provisioningService.createApplication(
                TENANT_ID,
                new ApplicationProvisioningService.CreateApplicationRequest("quota-app", "Quota App")
        );
        ApplicationProvisioningService.DomainDto domain = provisioningService.createApplicationDedicatedDomain(
                TENANT_ID,
                app.id(),
                "quota-app-" + System.nanoTime() + ".example.test"
        );

        jdbcTemplate.update(
                """
                        UPDATE application_quotas
                        SET monthly_link_limit = 1
                        WHERE application_id = ?
                        """,
                app.id()
        );

        ShortLinkService.CreateLinkRequest req = new ShortLinkService.CreateLinkRequest(
                "https://example.com/quota-a",
                null,
                null,
                null,
                "quotaA" + Long.toHexString(System.nanoTime()),
                java.util.Set.of(),
                null,
                null,
                null,
                null,
                null,
                app.id(),
                domain.id(),
                null
        );
        shortLinkService.create(TENANT_ID, ShortLinkService.CreatedBy.user(1L), req);

        ShortLinkService.CreateLinkRequest overLimitReq = new ShortLinkService.CreateLinkRequest(
                "https://example.com/quota-b",
                null,
                null,
                null,
                "quotaB" + Long.toHexString(System.nanoTime()),
                java.util.Set.of(),
                null,
                null,
                null,
                null,
                null,
                app.id(),
                domain.id(),
                null
        );

        Assertions.assertThrows(
                BusinessException.class,
                () -> shortLinkService.create(TENANT_ID, ShortLinkService.CreatedBy.user(1L), overLimitReq)
        );
    }
}
