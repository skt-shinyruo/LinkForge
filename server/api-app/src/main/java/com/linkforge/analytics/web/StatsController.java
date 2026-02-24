package com.linkforge.analytics.web;

import com.linkforge.analytics.service.AnalyticsQueryService;
import com.linkforge.platform.api.ApiResponse;
import com.linkforge.platform.api.BusinessException;
import com.linkforge.platform.api.ErrorCode;
import com.linkforge.platform.security.AuthContext;
import com.linkforge.platform.security.AuthPrincipal;
import com.linkforge.platform.web.RequestId;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final AnalyticsQueryService queryService;

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

    public StatsController(AnalyticsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/links/{id}/daily")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<AnalyticsQueryService.DailyStat>> linkDaily(
            @PathVariable("id") long linkId,
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to
    ) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "from 不能晚于 to");
        }
        AuthPrincipal p = AuthContext.requirePrincipal();
        List<AnalyticsQueryService.DailyStat> r = queryService.linkDaily(p.getTenantId(), linkId, from, to);
        return ApiResponse.ok(r, RequestId.get());
    }

    @GetMapping("/overview")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<AnalyticsQueryService.DailyStat>> overview(
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to
    ) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "from 不能晚于 to");
        }
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(queryService.tenantDaily(p.getTenantId(), from, to), RequestId.get());
    }

    @GetMapping("/top-links")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<AnalyticsQueryService.TopLinkStat>> topLinks(
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
        return ApiResponse.ok(queryService.topLinks(p.getTenantId(), from, to, l, s), RequestId.get());
    }

    @GetMapping("/links/{id}/dimensions")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<AnalyticsQueryService.DimensionStat>> linkDimensions(
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
        return ApiResponse.ok(queryService.linkDimensions(p.getTenantId(), linkId, from, to, t, l), RequestId.get());
    }

    @GetMapping("/links/{id}/events")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<AnalyticsQueryService.VisitEvent>> linkEvents(
            @PathVariable("id") long linkId,
            @RequestParam(value = "from", required = false) LocalDateTime from,
            @RequestParam(value = "to", required = false) LocalDateTime to,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        LocalDateTime t = (to == null ? LocalDateTime.now(ZoneOffset.UTC) : to);
        LocalDateTime f = (from == null ? t.minusDays(1) : from);
        if (f.isAfter(t)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "from 不能晚于 to");
        }

        int l = (limit == null ? 50 : limit);
        if (l < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "limit 必须 >= 1");
        }
        if (l > 200) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "limit 最大为 200");
        }

        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(queryService.linkEvents(p.getTenantId(), linkId, f, t, l), RequestId.get());
    }
}
