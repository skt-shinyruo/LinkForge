package com.linkforge.analytics.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.governance.ApprovalRequestView;
import com.linkforge.contract.governance.ApprovalSubmissionPort;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.shortlink.application.ShortLinkReadService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class AnalyticsExportRequestService {

    private final ApprovalSubmissionPort approvalSubmissionPort;
    private final ShortLinkReadService shortLinkReadService;
    private final Clock clock;

    public AnalyticsExportRequestService(
            ApprovalSubmissionPort approvalSubmissionPort,
            ShortLinkReadService shortLinkReadService,
            Clock clock
    ) {
        this.approvalSubmissionPort = approvalSubmissionPort;
        this.shortLinkReadService = shortLinkReadService;
        this.clock = clock;
    }

    public ApprovalRequestView requestLinkEventExport(
            UserActor actor,
            long linkId,
            Long expectedApplicationId,
            LocalDateTime from,
            LocalDateTime to
    ) {
        ShortLinkReadService.LinkOwnership link = requireLinkScope(actor.tenantId(), linkId);
        if (expectedApplicationId != null
                && (link.applicationId() == null || !expectedApplicationId.equals(link.applicationId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "链接不属于该应用");
        }

        LocalDateTime effectiveTo = to == null ? nowUtc() : to;
        LocalDateTime effectiveFrom = from == null ? effectiveTo.minusDays(1) : from;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "from 不能晚于 to");
        }

        return approvalSubmissionPort.requestAnalyticsDetailExportApproval(
                actor.tenantId(),
                new ApprovalSubmissionPort.AnalyticsDetailExportApprovalRequest(
                        linkId,
                        link.applicationId(),
                        effectiveFrom,
                        effectiveTo,
                        actor,
                        nowUtc()
                )
        );
    }

    private ShortLinkReadService.LinkOwnership requireLinkScope(long tenantId, long linkId) {
        return shortLinkReadService.findOwnership(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "链接不存在"));
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
