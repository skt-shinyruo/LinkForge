package com.linkforge.shortlink.application;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.governance.application.GovernanceService;
import com.linkforge.governance.domain.ApprovalStatus;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    ShortLinkApplicationService shortLinkService;

    @Autowired
    GovernanceService governanceService;

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
                tenantAdminActor(),
                new ApplicationProvisioningService.CreateApplicationRequest("order-center-" + suffix, "Order Center")
        );
        ApplicationProvisioningService.ApplicationDto otherApp = applicationProvisioningService.createApplication(
                TENANT_ID,
                tenantAdminActor(),
                new ApplicationProvisioningService.CreateApplicationRequest("campaign-center-" + suffix, "Campaign Center")
        );
        ApplicationProvisioningService.DomainDto domain = applicationProvisioningService.createTenantSharedDomain(
                TENANT_ID,
                tenantAdminActor(),
                "go-" + suffix + ".tenant.example"
        );
        applicationProvisioningService.authorizeDomain(TENANT_ID, tenantAdminActor(), app.id(), domain.id());

        applicationId = app.id();
        authorizedDomainId = domain.id();
        unauthorizedApplicationId = otherApp.id();
    }

    @Test
    void createLink_should_require_application_and_domain_ownership() {
        CreateLinkRequest request = new CreateLinkRequest(
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

        assertThatThrownBy(() -> shortLinkService.create(TENANT_ID, CreatedBy.user(USER_ID), request))
                .hasMessageContaining("应用未获授权使用该共享域名");
    }

    @Test
    void active_public_link_destination_change_should_create_request_instead_of_direct_mutation() {
        LinkDto created = shortLinkService.create(
                TENANT_ID,
                CreatedBy.user(USER_ID),
                new CreateLinkRequest(
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
                new UpdateLinkRequest(
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
    void approved_public_link_destination_change_should_mutate_original_url() {
        LinkDto created = shortLinkService.create(
                TENANT_ID,
                CreatedBy.user(USER_ID),
                new CreateLinkRequest(
                        "https://example.com/original-for-approval",
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
                new UpdateLinkRequest(
                        "https://example.com/approved-change",
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
        Long approvalId = jdbcTemplate.queryForObject(
                """
                        SELECT id
                        FROM approval_requests
                        WHERE operation_type = 'PUBLIC_LINK_DESTINATION_CHANGE'
                          AND target_application_id = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                Long.class,
                applicationId
        );
        String afterSnapshot = jdbcTemplate.queryForObject(
                "SELECT after_snapshot FROM approval_requests WHERE id = ?",
                String.class,
                approvalId
        );
        assertThat(afterSnapshot)
                .contains("\"type\":\"linkDestinationChange\"")
                .contains("\"version\":1")
                .contains("\"linkId\":" + created.id())
                .contains("\"originalUrl\":\"https://example.com/approved-change\"");

        GovernanceService.ApprovalRequestDto approved = governanceService.approveRequest(
                TENANT_ID,
                approvalId,
                "approved",
                new UserActor(TENANT_ID, USER_ID + 1, "approver@example.com", Set.of("TENANT_ADMIN")),
                LocalDateTime.now(ZoneOffset.UTC)
        );

        assertThat(approved.status()).isEqualTo(ApprovalStatus.EXECUTED);
        String originalUrl = jdbcTemplate.queryForObject(
                "SELECT original_url FROM short_links WHERE id = ?",
                String.class,
                created.id()
        );
        assertThat(originalUrl).isEqualTo("https://example.com/approved-change");
    }

    @Test
    void concurrent_public_link_destination_approval_should_execute_once_and_audit_once() throws Exception {
        LinkDto created = shortLinkService.create(
                TENANT_ID,
                CreatedBy.user(USER_ID),
                new CreateLinkRequest(
                        "https://example.com/concurrent-original",
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
                new UpdateLinkRequest(
                        "https://example.com/concurrent-approved",
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

        Long approvalId = jdbcTemplate.queryForObject(
                """
                        SELECT id
                        FROM approval_requests
                        WHERE operation_type = 'PUBLIC_LINK_DESTINATION_CHANGE'
                          AND target_application_id = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                Long.class,
                applicationId
        );
        Long versionBeforeApproval = jdbcTemplate.queryForObject(
                "SELECT version FROM short_links WHERE id = ?",
                Long.class,
                created.id()
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ApprovalAttempt> first = submitApprovalAttempt(
                    executor,
                    start,
                    () -> governanceService.approveRequest(
                            TENANT_ID,
                            approvalId,
                            "first",
                            new UserActor(TENANT_ID, USER_ID + 1, "first-approver@example.com", Set.of("TENANT_ADMIN")),
                            LocalDateTime.now(ZoneOffset.UTC)
                    )
            );
            Future<ApprovalAttempt> second = submitApprovalAttempt(
                    executor,
                    start,
                    () -> governanceService.approveRequest(
                            TENANT_ID,
                            approvalId,
                            "second",
                            new UserActor(TENANT_ID, USER_ID + 2, "second-approver@example.com", Set.of("TENANT_ADMIN")),
                            LocalDateTime.now(ZoneOffset.UTC)
                    )
            );

            start.countDown();

            ApprovalAttempt firstResult = first.get(10, TimeUnit.SECONDS);
            ApprovalAttempt secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(firstResult.success() ^ secondResult.success()).isTrue();
            assertThat(firstResult.success() ? firstResult.status() : secondResult.status()).isEqualTo(ApprovalStatus.EXECUTED);
            assertThat(firstResult.success() ? secondResult.errorMessage() : firstResult.errorMessage())
                    .contains("审批请求状态已变化");
        } finally {
            executor.shutdownNow();
        }

        String originalUrl = jdbcTemplate.queryForObject(
                "SELECT original_url FROM short_links WHERE id = ?",
                String.class,
                created.id()
        );
        Long versionAfterApproval = jdbcTemplate.queryForObject(
                "SELECT version FROM short_links WHERE id = ?",
                Long.class,
                created.id()
        );
        Long approveAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE request_id = ? AND action_type = 'APPROVE_REQUEST'",
                Long.class,
                approvalId
        );

        assertThat(originalUrl).isEqualTo("https://example.com/concurrent-approved");
        assertThat(versionAfterApproval).isEqualTo(versionBeforeApproval + 1);
        assertThat(approveAuditCount).isEqualTo(1L);
    }

    @Test
    void draft_link_should_allow_direct_edit_without_approval_state_on_link_row() {
        LinkDto created = shortLinkService.create(
                TENANT_ID,
                CreatedBy.user(USER_ID),
                new CreateLinkRequest(
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
                new UpdateLinkRequest(
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

    private static Future<ApprovalAttempt> submitApprovalAttempt(
            ExecutorService executor,
            CountDownLatch start,
            Callable<GovernanceService.ApprovalRequestDto> task
    ) {
        return executor.submit(() -> {
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                GovernanceService.ApprovalRequestDto dto = task.call();
                return new ApprovalAttempt(true, dto.status(), null);
            } catch (BusinessException ex) {
                return new ApprovalAttempt(false, null, ex.getMessage());
            }
        });
    }

    private record ApprovalAttempt(boolean success, ApprovalStatus status, String errorMessage) {
    }

    private static UserActor tenantAdminActor() {
        return new UserActor(TENANT_ID, USER_ID, "tenant-admin@example.com", Set.of("TENANT_ADMIN"));
    }
}
