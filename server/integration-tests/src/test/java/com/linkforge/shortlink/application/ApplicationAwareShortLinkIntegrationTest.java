package com.linkforge.shortlink.application;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.platform.application.ApplicationProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ApplicationAwareShortLinkIntegrationTest extends ApplicationAwareShortLinkIntegrationTestSupport {

    private static final long TENANT_ID = 83001L;
    private static final long USER_ID = 93001L;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ApplicationProvisioningService applicationProvisioningService;

    @Autowired
    ShortLinkService shortLinkService;

    private long applicationId;
    private long authorizedDomainId;
    private long unauthorizedApplicationId;

    @BeforeEach
    void setUpTenantApplicationAndAuth() {
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, TENANT_ID);
        authenticateAsTenantAdmin(TENANT_ID, USER_ID, "tenant-admin@example.com");
        String suffix = Long.toUnsignedString(System.nanoTime());

        ApplicationProvisioningService.ApplicationDto app = applicationProvisioningService.createApplication(
                TENANT_ID,
                new ApplicationProvisioningService.CreateApplicationRequest("order-center-" + suffix, "Order Center")
        );
        ApplicationProvisioningService.ApplicationDto otherApp = applicationProvisioningService.createApplication(
                TENANT_ID,
                new ApplicationProvisioningService.CreateApplicationRequest("campaign-center-" + suffix, "Campaign Center")
        );
        ApplicationProvisioningService.DomainDto domain = applicationProvisioningService.createTenantSharedDomain(TENANT_ID, "go-" + suffix + ".tenant.example");
        applicationProvisioningService.authorizeDomain(TENANT_ID, app.id(), domain.id());

        applicationId = app.id();
        authorizedDomainId = domain.id();
        unauthorizedApplicationId = otherApp.id();
    }

    @Test
    void createLink_should_require_application_and_domain_ownership() {
        ShortLinkService.CreateLinkRequest request = new ShortLinkService.CreateLinkRequest(
                "https://example.com/app-aware",
                "note",
                null,
                null,
                null,
                Set.of(),
                null,
                null,
                null,
                null,
                null,
                unauthorizedApplicationId,
                authorizedDomainId,
                "ACTIVE"
        );

        assertThatThrownBy(() -> shortLinkService.create(TENANT_ID, ShortLinkService.CreatedBy.user(USER_ID), request))
                .hasMessageContaining("应用未获授权使用该共享域名");
    }

    @Test
    void active_public_link_destination_change_should_create_request_instead_of_direct_mutation() {
        ShortLinkService.LinkDto created = shortLinkService.create(
                TENANT_ID,
                ShortLinkService.CreatedBy.user(USER_ID),
                new ShortLinkService.CreateLinkRequest(
                        "https://example.com/original",
                        "note",
                        null,
                        null,
                        null,
                        Set.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        applicationId,
                        authorizedDomainId,
                        "ACTIVE"
                )
        );

        shortLinkService.update(
                TENANT_ID,
                created.id(),
                new ShortLinkService.UpdateLinkRequest(
                        "https://example.com/changed",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                tenantAdminActor(),
                LocalDateTime.now(ZoneOffset.UTC)
        );

        String originalUrl = jdbcTemplate.queryForObject(
                "SELECT original_url FROM short_links WHERE id = ?",
                String.class,
                created.id()
        );
        assertThat(originalUrl).isEqualTo("https://example.com/original");

        Integer requestCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_requests WHERE operation_type = 'PUBLIC_LINK_DESTINATION_CHANGE' AND target_application_id = ?",
                Integer.class,
                applicationId
        );
        assertThat(requestCount).isEqualTo(1);
    }

    @Test
    void draft_link_should_allow_direct_edit_without_approval_state_on_link_row() {
        ShortLinkService.LinkDto created = shortLinkService.create(
                TENANT_ID,
                ShortLinkService.CreatedBy.user(USER_ID),
                new ShortLinkService.CreateLinkRequest(
                        "https://example.com/draft",
                        "note",
                        null,
                        null,
                        null,
                        Set.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        applicationId,
                        authorizedDomainId,
                        "DRAFT"
                )
        );

        shortLinkService.update(
                TENANT_ID,
                created.id(),
                new ShortLinkService.UpdateLinkRequest(
                        "https://example.com/draft-updated",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                tenantAdminActor(),
                LocalDateTime.now(ZoneOffset.UTC)
        );

        String originalUrl = jdbcTemplate.queryForObject(
                "SELECT original_url FROM short_links WHERE id = ?",
                String.class,
                created.id()
        );
        assertThat(originalUrl).isEqualTo("https://example.com/draft-updated");

        Integer requestCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_requests WHERE target_application_id = ?",
                Integer.class,
                applicationId
        );
        assertThat(requestCount).isZero();
    }

    private static UserActor tenantAdminActor() {
        return new UserActor(TENANT_ID, USER_ID, "tenant-admin@example.com", Set.of("TENANT_ADMIN"));
    }
}
