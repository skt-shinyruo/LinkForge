package com.linkforge.analytics.interfaces.web;

import com.linkforge.analytics.application.AnalyticsExportRequestService;
import com.linkforge.analytics.application.AnalyticsLinkEventsService;
import com.linkforge.analytics.application.AnalyticsQueryService;
import com.linkforge.analytics.application.AnalyticsReportingService;
import com.linkforge.contract.api.ApiResponse;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.governance.ApprovalRequestView;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.runtime.security.AuthContext;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.runtime.security.PrincipalActorMapper;
import com.linkforge.foundation.web.RequestId;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
public class StatsController {

    private final AnalyticsQueryService queryService;
    private final AnalyticsReportingService reportingService;
    private final AnalyticsLinkEventsService linkEventsService;
    private final AnalyticsExportRequestService exportRequestService;
    private final PrincipalActorMapper principalActorMapper;

    private static final Set<String> DIM_TYPES = Set.of(
            "referer_domain",
            "language",
            "ua_family",
            "os_family",
            "device_type",
            "utm_source",
            "utm_medium",
            "utm_campaign"
    );

    public StatsController(
            AnalyticsQueryService queryService,
            AnalyticsReportingService reportingService,
            AnalyticsLinkEventsService linkEventsService,
            AnalyticsExportRequestService exportRequestService,
            PrincipalActorMapper principalActorMapper
    ) {
        this.queryService = queryService;
        this.reportingService = reportingService;
        this.linkEventsService = linkEventsService;
        this.exportRequestService = exportRequestService;
        this.principalActorMapper = principalActorMapper;
    }

    @GetMapping("/stats/links/{id}/daily")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<DailyStatHttpResponse>> linkDaily(
            @PathVariable("id") long linkId,
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to
    ) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "from 不能晚于 to");
        }
        AuthPrincipal p = AuthContext.requirePrincipal();
        List<DailyStatHttpResponse> r = queryService.linkDaily(p.getTenantId(), linkId, from, to).stream()
                .map(AnalyticsHttpMapper::toDailyStatResponse)
                .toList();
        return ApiResponse.ok(r, RequestId.get());
    }

    @GetMapping("/stats/overview")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<DailyStatHttpResponse>> overview(
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to
    ) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "from 不能晚于 to");
        }
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(queryService.tenantDaily(p.getTenantId(), from, to).stream()
                .map(AnalyticsHttpMapper::toDailyStatResponse)
                .toList(), RequestId.get());
    }

    @GetMapping("/stats/applications/{id}/overview")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<DailyStatHttpResponse>> applicationOverview(
            @PathVariable("id") long applicationId,
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to
    ) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "from 不能晚于 to");
        }
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(queryService.applicationDaily(p.getTenantId(), applicationId, from, to).stream()
                .map(AnalyticsHttpMapper::toDailyStatResponse)
                .toList(), RequestId.get());
    }

    @GetMapping("/applications/{id}/stats/overview")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<DailyStatHttpResponse>> applicationOverviewAlias(
            @PathVariable("id") long applicationId,
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to
    ) {
        return applicationOverview(applicationId, from, to);
    }

    @GetMapping("/stats/domains/{id}/overview")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<DailyStatHttpResponse>> domainOverview(
            @PathVariable("id") long domainId,
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to
    ) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "from 不能晚于 to");
        }
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(queryService.domainDaily(p.getTenantId(), domainId, from, to).stream()
                .map(AnalyticsHttpMapper::toDailyStatResponse)
                .toList(), RequestId.get());
    }

    @GetMapping("/stats/top-links")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<TopLinkStatHttpResponse>> topLinks(
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "sortBy", required = false) String sortBy
    ) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "from 不能晚于 to");
        }
        int l = (limit == null ? 10 : limit);
        if (l < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "limit 必须 >= 1");
        }
        if (l > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "limit 最大为 100");
        }

        AnalyticsQueryService.TopSortBy s = AnalyticsQueryService.TopSortBy.PV;
        if (sortBy != null && !sortBy.isBlank()) {
            String raw = sortBy.trim().toLowerCase();
            if ("pv".equals(raw)) {
                s = AnalyticsQueryService.TopSortBy.PV;
            } else if ("uv".equals(raw)) {
                s = AnalyticsQueryService.TopSortBy.UV;
            } else {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "sortBy 仅支持 pv/uv");
            }
        }
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(reportingService.topLinks(p.getTenantId(), from, to, l, s).stream()
                .map(AnalyticsHttpMapper::toTopLinkStatResponse)
                .toList(), RequestId.get());
    }

    @GetMapping("/stats/applications/{id}/top-links")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<TopLinkStatHttpResponse>> applicationTopLinks(
            @PathVariable("id") long applicationId,
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "sortBy", required = false) String sortBy
    ) {
        AnalyticsQueryService.TopSortBy sort = resolveTopSortBy(from, to, limit, sortBy);
        int l = normalizeTopLimit(limit);
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(reportingService.applicationTopLinks(p.getTenantId(), applicationId, from, to, l, sort).stream()
                .map(AnalyticsHttpMapper::toTopLinkStatResponse)
                .toList(), RequestId.get());
    }

    @GetMapping("/applications/{id}/stats/top-links")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<TopLinkStatHttpResponse>> applicationTopLinksAlias(
            @PathVariable("id") long applicationId,
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "sortBy", required = false) String sortBy
    ) {
        return applicationTopLinks(applicationId, from, to, limit, sortBy);
    }

    @GetMapping("/stats/domains/{id}/top-links")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<TopLinkStatHttpResponse>> domainTopLinks(
            @PathVariable("id") long domainId,
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "sortBy", required = false) String sortBy
    ) {
        AnalyticsQueryService.TopSortBy sort = resolveTopSortBy(from, to, limit, sortBy);
        int l = normalizeTopLimit(limit);
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(reportingService.domainTopLinks(p.getTenantId(), domainId, from, to, l, sort).stream()
                .map(AnalyticsHttpMapper::toTopLinkStatResponse)
                .toList(), RequestId.get());
    }

    @GetMapping("/stats/links/{id}/dimensions")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<DimensionStatHttpResponse>> linkDimensions(
            @PathVariable("id") long linkId,
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to,
            @RequestParam("type") String type,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "from 不能晚于 to");
        }
        String t = type == null ? null : type.trim().toLowerCase();
        if (t == null || t.isBlank() || !DIM_TYPES.contains(t)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "type 不合法（支持: " + String.join(",", DIM_TYPES) + "）");
        }

        int l = (limit == null ? 10 : limit);
        if (l < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "limit 必须 >= 1");
        }
        if (l > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "limit 最大为 100");
        }

        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(queryService.linkDimensions(p.getTenantId(), linkId, from, to, t, l).stream()
                .map(AnalyticsHttpMapper::toDimensionStatResponse)
                .toList(), RequestId.get());
    }

    @GetMapping("/stats/links/{id}/events")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<VisitEventHttpResponse>> linkEvents(
            @PathVariable("id") long linkId,
            @RequestParam(value = "from", required = false) LocalDateTime from,
            @RequestParam(value = "to", required = false) LocalDateTime to,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        UserActor actor = principalActorMapper.requireUser(AuthContext.requirePrincipal());
        return ApiResponse.ok(linkEventsService.listLinkEvents(actor, linkId, from, to, limit).stream()
                .map(AnalyticsHttpMapper::toVisitEventResponse)
                .toList(), RequestId.get());
    }

    @PostMapping("/stats/links/{id}/events/export-requests")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<ApprovalRequestView> requestEventExport(
            @PathVariable("id") long linkId,
            @RequestParam(value = "from", required = false) LocalDateTime from,
            @RequestParam(value = "to", required = false) LocalDateTime to
    ) {
        UserActor actor = principalActorMapper.requireUser(AuthContext.requirePrincipal());
        return ApiResponse.ok(
                exportRequestService.requestLinkEventExport(actor, linkId, null, from, to),
                RequestId.get()
        );
    }

    @PostMapping("/applications/{applicationId}/links/{id}/events/export-requests")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<ApprovalRequestView> requestEventExportByApplication(
            @PathVariable("applicationId") long applicationId,
            @PathVariable("id") long linkId,
            @RequestParam(value = "from", required = false) LocalDateTime from,
            @RequestParam(value = "to", required = false) LocalDateTime to
    ) {
        UserActor actor = principalActorMapper.requireUser(AuthContext.requirePrincipal());
        return ApiResponse.ok(
                exportRequestService.requestLinkEventExport(actor, linkId, applicationId, from, to),
                RequestId.get()
        );
    }

    private static AnalyticsQueryService.TopSortBy resolveTopSortBy(LocalDate from, LocalDate to, Integer limit, String sortBy) {
        validateDateRange(from, to);
        normalizeTopLimit(limit);
        AnalyticsQueryService.TopSortBy s = AnalyticsQueryService.TopSortBy.PV;
        if (sortBy != null && !sortBy.isBlank()) {
            String raw = sortBy.trim().toLowerCase();
            if ("pv".equals(raw)) {
                s = AnalyticsQueryService.TopSortBy.PV;
            } else if ("uv".equals(raw)) {
                s = AnalyticsQueryService.TopSortBy.UV;
            } else {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "sortBy 仅支持 pv/uv");
            }
        }
        return s;
    }

    private static int normalizeTopLimit(Integer limit) {
        int l = (limit == null ? 10 : limit);
        if (l < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "limit 必须 >= 1");
        }
        if (l > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "limit 最大为 100");
        }
        return l;
    }

    private static void validateDateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "from 不能晚于 to");
        }
    }

}
