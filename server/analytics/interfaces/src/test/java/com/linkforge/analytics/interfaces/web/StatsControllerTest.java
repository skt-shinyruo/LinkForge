package com.linkforge.analytics.interfaces.web;

import com.linkforge.analytics.application.AnalyticsQueryService;
import com.linkforge.contract.api.ApiResponse;
import com.linkforge.contract.governance.ApprovalRequestView;
import com.linkforge.contract.governance.ApprovalSubmissionPort;
import com.linkforge.contract.governance.SensitiveOperation;
import com.linkforge.contract.shortlink.ShortLinkOwnershipLookupPort;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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
                .contains(ApprovalSubmissionPort.class);
        assertThat(constructor.getParameterTypes())
                .extracting(Class::getName)
                .doesNotContain("com.linkforge.governance.application.GovernanceService");
    }

    @Test
    void requestEventExport_shouldSubmitApproval_viaGovernanceContract() {
        AnalyticsQueryService queryService = mock(AnalyticsQueryService.class);
        ApprovalSubmissionPort approvalSubmissionPort = mock(ApprovalSubmissionPort.class);
        ShortLinkOwnershipLookupPort shortLinkOwnershipLookupPort = mock(ShortLinkOwnershipLookupPort.class);
        StatsController controller = new StatsController(queryService, approvalSubmissionPort, shortLinkOwnershipLookupPort);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(9L, 1L, "tenant-admin@example.com", Set.of("TENANT_ADMIN")),
                "N/A",
                java.util.List.of()
        ));
        RequestId.set("req-123");

        LocalDateTime from = LocalDateTime.parse("2026-03-30T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-03-31T00:00:00");
        when(shortLinkOwnershipLookupPort.findByTenantIdAndId(1L, 101L))
                .thenReturn(Optional.of(new ShortLinkOwnershipLookupPort.ShortLinkOwnership(2001L, 3001L)));

        ApprovalRequestView expected = new ApprovalRequestView(
                501L,
                1L,
                SensitiveOperation.ANALYTICS_DETAIL_EXPORT,
                2001L,
                9L,
                "tenant-admin@example.com",
                "PENDING_APPROVAL",
                null,
                null,
                null
        );
        when(approvalSubmissionPort.submitRequest(
                1L,
                SensitiveOperation.ANALYTICS_DETAIL_EXPORT,
                2001L,
                null,
                "linkId=101,from=2026-03-30T00:00,to=2026-03-31T00:00"
        )).thenReturn(expected);

        ApiResponse<ApprovalRequestView> response = controller.requestEventExport(101L, from, to);

        assertThat(response.getData()).isSameAs(expected);
        assertThat(response.getRequestId()).isEqualTo("req-123");
        verify(approvalSubmissionPort).submitRequest(
                1L,
                SensitiveOperation.ANALYTICS_DETAIL_EXPORT,
                2001L,
                null,
                "linkId=101,from=2026-03-30T00:00,to=2026-03-31T00:00"
        );
    }
}
