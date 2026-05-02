package com.linkforge.analytics.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.security.StandardRoles;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

@Service
public class AnalyticsLinkEventsService {

    private static final int MAX_EVENTS_QUERY_DAYS = 7;

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
        requireAdmin(actor);
        LocalDateTime effectiveTo = to == null ? nowUtc() : to;
        LocalDateTime effectiveFrom = from == null ? effectiveTo.minusDays(1) : from;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "from 不能晚于 to");
        }
        if (effectiveFrom.plusDays(MAX_EVENTS_QUERY_DAYS).isBefore(effectiveTo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "事件明细查询时间范围最大为 7 天");
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

    private static void requireAdmin(UserActor actor) {
        if (actor == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "actor 无效");
        }
        Set<String> roles = actor.roles() == null ? Set.of() : actor.roles();
        if (!roles.contains(StandardRoles.TENANT_ADMIN) && !roles.contains(StandardRoles.PLATFORM_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "访问明细需要管理员权限");
        }
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
