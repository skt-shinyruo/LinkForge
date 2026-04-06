package com.linkforge.analytics.interfaces.web;

import com.linkforge.analytics.application.AnalyticsQueryService;
import com.linkforge.analytics.application.AnalyticsExportRequestService;
import com.linkforge.analytics.application.AnalyticsLinkEventsService;
import com.linkforge.contract.api.ApiResponse;
import com.linkforge.contract.governance.ApprovalRequestView;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.security.PrincipalActorMapper;
import com.linkforge.foundation.web.RequestId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatsControllerTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestId.clear();
    }

    @Test
    void constructor_shouldDependOnApprovalSubmissionPort_insteadOfGovernanceService() {
        Constructor<?> constructor = StatsController.class.getDeclaredConstructors()[0];

        assertThat(constructor.getParameterTypes())
                .contains(AnalyticsExportRequestService.class, AnalyticsLinkEventsService.class, PrincipalActorMapper.class);
        assertThat(constructor.getParameterTypes())
                .extracting(Class::getName)
                .doesNotContain(
                        "com.linkforge.governance.application.GovernanceService",
                        "com.linkforge.contract.governance.ApprovalSubmissionPort",
                        "com.linkforge.contract.shortlink.ShortLinkOwnershipLookupPort"
                );
    }

    @Test
    void requestEventExport_shouldDelegateToAnalyticsExportRequestService() {
        AnalyticsQueryService queryService = mock(AnalyticsQueryService.class);
        AnalyticsLinkEventsService linkEventsService = mock(AnalyticsLinkEventsService.class);
        AnalyticsExportRequestService exportRequestService = mock(AnalyticsExportRequestService.class);
        PrincipalActorMapper principalActorMapper = mock(PrincipalActorMapper.class);
        StatsController controller = new StatsController(queryService, linkEventsService, exportRequestService, principalActorMapper);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(9L, 1L, "tenant-admin@example.com", Set.of("TENANT_ADMIN")),
                "N/A",
                java.util.List.of()
        ));
        RequestId.set("req-123");

        UserActor actor = new UserActor(1L, 9L, "tenant-admin@example.com", Set.of("TENANT_ADMIN"));
        LocalDateTime from = LocalDateTime.parse("2026-03-30T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-03-31T00:00:00");
        when(principalActorMapper.requireUser(any(AuthPrincipal.class))).thenReturn(actor);

        ApprovalRequestView expected = new ApprovalRequestView(
                501L,
                1L,
                null,
                2001L,
                9L,
                "tenant-admin@example.com",
                "PENDING_APPROVAL",
                null,
                null,
                null
        );
        when(exportRequestService.requestLinkEventExport(actor, 101L, null, from, to)).thenReturn(expected);

        ApiResponse<ApprovalRequestView> response = controller.requestEventExport(101L, from, to);

        assertThat(response.getData()).isSameAs(expected);
        assertThat(response.getRequestId()).isEqualTo("req-123");
        verify(exportRequestService).requestLinkEventExport(actor, 101L, null, from, to);
    }

    @Test
    void requestEventExportByApplication_shouldDelegateExpectedApplicationScope() {
        AnalyticsQueryService queryService = mock(AnalyticsQueryService.class);
        AnalyticsLinkEventsService linkEventsService = mock(AnalyticsLinkEventsService.class);
        AnalyticsExportRequestService exportRequestService = mock(AnalyticsExportRequestService.class);
        PrincipalActorMapper principalActorMapper = mock(PrincipalActorMapper.class);
        StatsController controller = new StatsController(queryService, linkEventsService, exportRequestService, principalActorMapper);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(9L, 1L, "tenant-admin@example.com", Set.of("TENANT_ADMIN")),
                "N/A",
                java.util.List.of()
        ));

        UserActor actor = new UserActor(1L, 9L, "tenant-admin@example.com", Set.of("TENANT_ADMIN"));
        LocalDateTime from = LocalDateTime.parse("2026-03-30T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-03-31T00:00:00");
        when(principalActorMapper.requireUser(any(AuthPrincipal.class))).thenReturn(actor);
        ApprovalRequestView expected = new ApprovalRequestView(
                502L,
                1L,
                null,
                2001L,
                9L,
                "tenant-admin@example.com",
                "PENDING_APPROVAL",
                null,
                null,
                null
        );
        when(exportRequestService.requestLinkEventExport(actor, 101L, 2001L, from, to)).thenReturn(expected);

        ApiResponse<ApprovalRequestView> response = controller.requestEventExportByApplication(2001L, 101L, from, to);

        assertThat(response.getData()).isSameAs(expected);
        verify(exportRequestService).requestLinkEventExport(actor, 101L, 2001L, from, to);
    }

    @Test
    void linkEvents_shouldDelegateToApplicationServiceWithoutControllerDefaulting() {
        AnalyticsQueryService queryService = mock(AnalyticsQueryService.class);
        AnalyticsLinkEventsService linkEventsService = mock(AnalyticsLinkEventsService.class);
        AnalyticsExportRequestService exportRequestService = mock(AnalyticsExportRequestService.class);
        PrincipalActorMapper principalActorMapper = mock(PrincipalActorMapper.class);
        StatsController controller = new StatsController(queryService, linkEventsService, exportRequestService, principalActorMapper);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(9L, 1L, "tenant-admin@example.com", Set.of("TENANT_ADMIN")),
                "N/A",
                java.util.List.of()
        ));
        RequestId.set("req-456");

        UserActor actor = new UserActor(1L, 9L, "tenant-admin@example.com", Set.of("TENANT_ADMIN"));
        LocalDateTime from = LocalDateTime.parse("2026-03-30T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-03-31T00:00:00");
        List<AnalyticsQueryService.VisitEvent> expected = List.of();
        when(principalActorMapper.requireUser(any(AuthPrincipal.class))).thenReturn(actor);
        when(linkEventsService.listLinkEvents(actor, 101L, from, to, 123)).thenReturn(expected);

        ApiResponse<List<AnalyticsQueryService.VisitEvent>> response = controller.linkEvents(101L, from, to, 123);

        assertThat(response.getData()).isSameAs(expected);
        assertThat(response.getRequestId()).isEqualTo("req-456");
        verify(linkEventsService).listLinkEvents(actor, 101L, from, to, 123);
    }
}
