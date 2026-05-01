package com.linkforge.analytics.application;

import com.linkforge.analytics.domain.AggregationWindow;
import com.linkforge.analytics.domain.AnalyticsExportPolicy;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.governance.ApprovalRequestView;
import com.linkforge.contract.governance.ApprovalSubmissionPort;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
import com.linkforge.foundation.context.UserActor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class AnalyticsExportRequestService {

    private final ApprovalSubmissionPort approvalSubmissionPort;
    private final ShortLinkReadPort shortLinkReadPort;
    private final Clock clock;
    private final AnalyticsExportPolicy exportPolicy;

    public AnalyticsExportRequestService(
            ApprovalSubmissionPort approvalSubmissionPort,
            ShortLinkReadPort shortLinkReadPort,
            Clock clock
    ) {
        this(approvalSubmissionPort, shortLinkReadPort, clock, new AnalyticsExportPolicy());
    }

    AnalyticsExportRequestService(
            ApprovalSubmissionPort approvalSubmissionPort,
            ShortLinkReadPort shortLinkReadPort,
            Clock clock,
            AnalyticsExportPolicy exportPolicy
    ) {
        this.approvalSubmissionPort = approvalSubmissionPort;
        this.shortLinkReadPort = shortLinkReadPort;
        this.clock = clock;
        this.exportPolicy = exportPolicy;
    }

    public ApprovalRequestView requestLinkEventExport(
            UserActor actor,
            long linkId,
            Long expectedApplicationId,
            LocalDateTime from,
            LocalDateTime to
    ) {
        ShortLinkReadPort.ShortLinkOwnership link = requireLinkScope(actor.tenantId(), linkId);
        if (expectedApplicationId != null
                && (link.applicationId() == null || !expectedApplicationId.equals(link.applicationId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "链接不属于该应用");
        }

        LocalDateTime requestedAt = nowUtc();
        AggregationWindow window;
        try {
            window = exportPolicy.resolveWindow(
                    toInstant(from),
                    toInstant(to),
                    requestedAt.toInstant(ZoneOffset.UTC)
            );
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "from 不能晚于 to");
        }
        LocalDateTime effectiveFrom = LocalDateTime.ofInstant(window.fromInclusive(), ZoneOffset.UTC);
        LocalDateTime effectiveTo = LocalDateTime.ofInstant(window.toExclusive(), ZoneOffset.UTC);

        return approvalSubmissionPort.requestAnalyticsDetailExportApproval(
                actor.tenantId(),
                new ApprovalSubmissionPort.AnalyticsDetailExportApprovalRequest(
                        linkId,
                        link.applicationId(),
                        effectiveFrom,
                        effectiveTo,
                        actor,
                        requestedAt
                )
        );
    }

    private ShortLinkReadPort.ShortLinkOwnership requireLinkScope(long tenantId, long linkId) {
        return shortLinkReadPort.findOwnership(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "链接不存在"));
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }
}
