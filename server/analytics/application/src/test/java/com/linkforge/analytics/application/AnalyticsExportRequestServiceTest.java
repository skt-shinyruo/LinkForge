package com.linkforge.analytics.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.governance.ApprovalSubmissionPort;
import com.linkforge.contract.shortlink.ShortLinkOwnershipLookupPort;
import com.linkforge.foundation.context.UserActor;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalyticsExportRequestServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-04-06T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void requestLinkEventExport_shouldCheckMissingLinkBeforeDateValidation() {
        ApprovalSubmissionPort approvalSubmissionPort = mock(ApprovalSubmissionPort.class);
        ShortLinkOwnershipLookupPort shortLinkOwnershipLookupPort = mock(ShortLinkOwnershipLookupPort.class);
        AnalyticsExportRequestService service = new AnalyticsExportRequestService(
                approvalSubmissionPort,
                shortLinkOwnershipLookupPort,
                FIXED_CLOCK
        );

        when(shortLinkOwnershipLookupPort.findByTenantIdAndId(1L, 101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestLinkEventExport(
                new UserActor(1L, 9L, "tenant-admin@example.com", Set.of("TENANT_ADMIN")),
                101L,
                2001L,
                LocalDateTime.parse("2026-04-07T00:00:00"),
                LocalDateTime.parse("2026-04-06T00:00:00")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        verifyNoInteractions(approvalSubmissionPort);
    }

    @Test
    void requestLinkEventExport_shouldCheckApplicationScopeBeforeDateValidation() {
        ApprovalSubmissionPort approvalSubmissionPort = mock(ApprovalSubmissionPort.class);
        ShortLinkOwnershipLookupPort shortLinkOwnershipLookupPort = mock(ShortLinkOwnershipLookupPort.class);
        AnalyticsExportRequestService service = new AnalyticsExportRequestService(
                approvalSubmissionPort,
                shortLinkOwnershipLookupPort,
                FIXED_CLOCK
        );

        when(shortLinkOwnershipLookupPort.findByTenantIdAndId(1L, 101L))
                .thenReturn(Optional.of(new ShortLinkOwnershipLookupPort.ShortLinkOwnership(3001L, null)));

        assertThatThrownBy(() -> service.requestLinkEventExport(
                new UserActor(1L, 9L, "tenant-admin@example.com", Set.of("TENANT_ADMIN")),
                101L,
                2001L,
                LocalDateTime.parse("2026-04-07T00:00:00"),
                LocalDateTime.parse("2026-04-06T00:00:00")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(approvalSubmissionPort);
    }
}
