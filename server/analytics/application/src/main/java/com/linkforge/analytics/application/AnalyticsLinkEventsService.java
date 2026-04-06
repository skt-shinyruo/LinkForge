package com.linkforge.analytics.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.context.UserActor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class AnalyticsLinkEventsService {

    private final AnalyticsQueryService analyticsQueryService;
    private final Clock clock;

    public AnalyticsLinkEventsService(AnalyticsQueryService analyticsQueryService, Clock clock) {
        this.analyticsQueryService = analyticsQueryService;
        this.clock = clock;
    }

    public List<AnalyticsQueryService.VisitEvent> listLinkEvents(
            UserActor actor,
            long linkId,
            LocalDateTime from,
            LocalDateTime to,
            Integer limit
    ) {
        LocalDateTime effectiveTo = to == null ? nowUtc() : to;
        LocalDateTime effectiveFrom = from == null ? effectiveTo.minusDays(1) : from;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "from 不能晚于 to");
        }

        int effectiveLimit = limit == null ? 50 : limit;
        if (effectiveLimit < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "limit 必须 >= 1");
        }
        if (effectiveLimit > 200) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "limit 最大为 200");
        }

        return analyticsQueryService.linkEvents(actor.tenantId(), linkId, effectiveFrom, effectiveTo, effectiveLimit);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
