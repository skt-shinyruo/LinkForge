package com.linkforge.platform;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.platform.application.ApplicationResult;
import com.linkforge.platform.application.ApplicationProvisioningService;
import com.linkforge.platform.application.CreateApplicationCommand;
import com.linkforge.platform.application.DomainResult;
import com.linkforge.platform.application.PlatformControlPlaneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class DomainAuthorizationIntegrationTest extends PlatformPersistenceIntegrationTestSupport {

    private static final long TENANT_ID = 81002L;

    @Autowired
    ApplicationProvisioningService provisioningService;

    @Autowired
    PlatformControlPlaneService controlPlaneService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpTenantAndAuth() {
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, TENANT_ID);
        authenticateAsTenantAdmin(TENANT_ID);
    }

    @Test
    void createTenantSharedDomain_should_allow_authorization_to_multiple_applications() {
        ApplicationResult firstApplication = provisioningService.createApplication(
                TENANT_ID,
                tenantAdminActor(TENANT_ID),
                new CreateApplicationCommand("api-gateway", "API Gateway")
        );
        ApplicationResult secondApplication = provisioningService.createApplication(
                TENANT_ID,
                tenantAdminActor(TENANT_ID),
                new CreateApplicationCommand("campaign-console", "Campaign Console")
        );
        DomainResult domain = provisioningService.createTenantSharedDomain(
                TENANT_ID,
                tenantAdminActor(TENANT_ID),
                "go.tenant.example"
        );

        provisioningService.authorizeDomain(TENANT_ID, tenantAdminActor(TENANT_ID), firstApplication.id(), domain.id());
        provisioningService.authorizeDomain(TENANT_ID, tenantAdminActor(TENANT_ID), secondApplication.id(), domain.id());

        Long authorizationCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM application_domain_authorizations
                        WHERE domain_id = ?
                        """,
                Long.class,
                domain.id()
        );
        assertThat(authorizationCount).isEqualTo(2L);

        String scope = jdbcTemplate.queryForObject(
                """
                        SELECT scope
                        FROM domains
                        WHERE id = ?
                        """,
                String.class,
                domain.id()
        );
        assertThat(scope).isEqualTo("TENANT_SHARED");
    }

    @Test
    void listDomainsForApplication_shouldExcludeInactiveAuthorizedDomains() {
        ApplicationResult application = provisioningService.createApplication(
                TENANT_ID,
                tenantAdminActor(TENANT_ID),
                new CreateApplicationCommand("landing-pages", "Landing Pages")
        );
        DomainResult activeDomain = provisioningService.createTenantSharedDomain(
                TENANT_ID,
                tenantAdminActor(TENANT_ID),
                "active.tenant.example"
        );
        DomainResult inactiveDomain = provisioningService.createTenantSharedDomain(
                TENANT_ID,
                tenantAdminActor(TENANT_ID),
                "inactive.tenant.example"
        );
        provisioningService.authorizeDomain(TENANT_ID, tenantAdminActor(TENANT_ID), application.id(), activeDomain.id());
        provisioningService.authorizeDomain(TENANT_ID, tenantAdminActor(TENANT_ID), application.id(), inactiveDomain.id());
        jdbcTemplate.update(
                """
                        UPDATE domains
                        SET status = 'DISABLED'
                        WHERE id = ?
                        """,
                inactiveDomain.id()
        );

        assertThat(controlPlaneService.listDomainsForApplication(TENANT_ID, application.id()))
                .extracting(DomainResult::id)
                .contains(activeDomain.id())
                .doesNotContain(inactiveDomain.id());
    }
}
