package com.linkforge.analytics.interfaces.web;

import com.linkforge.analytics.application.AnalyticsQueryService;
import com.linkforge.analytics.application.ReportRange;
import com.linkforge.analytics.application.AnalyticsReportingApplicationService;
import com.linkforge.contract.api.ApiResponse;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.runtime.security.AuthContext;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计报表与访问明细审批的 HTTP 边界。
 *
 * <p>所有端点都从 {@link AuthContext} 取得已认证主体的 tenantId，绝不接受客户端提供的租户
 * 参数；查询、排序和资源归属因此始终在当前租户范围内执行。{@code OPENAPI} 角色不能访问这些
 * 管理端报表端点，避免 API Key 意外获得统计明细能力。
 *
 * <p>日报参数使用 UTC {@link LocalDate}，首尾日期均包含。所有成功响应携带当前
 * {@link RequestId}，便于与异步统计链路的日志关联。
 */
@RestController
@RequestMapping("/api/v1")
public class StatsController {

    private final AnalyticsQueryService queryService;
    private final AnalyticsReportingApplicationService reportingService;

    public StatsController(
            AnalyticsQueryService queryService,
            AnalyticsReportingApplicationService reportingService
    ) {
        this.queryService = queryService;
        this.reportingService = reportingService;
    }

    /**
     * 返回指定短链在当前租户内按 UTC 日聚合的 PV/UV。
     *
     * <p>路由为 {@code GET /api/v1/stats/links/{id}/daily}。{@code from} 与 {@code to}
     * 必填且均包含；{@code from > to} 返回 {@code BAD_REQUEST}。返回的日 UV 是 HyperLogLog
     * 近似值，不应被解释为精确去重人数。
     *
     * @param linkId 当前租户内的短链 ID
     * @param from 起始 UTC 日期，包含
     * @param to 结束 UTC 日期，包含
     * @return 带请求关联 ID 的日报列表
     */
    @GetMapping("/stats/links/{id}/daily")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<AnalyticsQueryService.DailyStat>> linkDaily(
            @PathVariable("id") long linkId,
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to
    ) {
        validateDateRange(from, to);
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(queryService.linkDaily(p.getTenantId(), linkId, from, to), RequestId.get());
    }

    /**
     * 返回当前租户全部短链的 UTC 日报。
     *
     * <p>路由为 {@code GET /api/v1/stats/overview}。日期校验与短链日报一致；scope HLL
     * 记录尚未落库时，底层读模型可能回退为链接日 UV 之和，因此该数值仍是近似口径。
     *
     * @param from 起始 UTC 日期，包含
     * @param to 结束 UTC 日期，包含
     * @return 当前认证租户的日报列表
     */
    @GetMapping("/stats/overview")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<AnalyticsQueryService.DailyStat>> overview(
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to
    ) {
        validateDateRange(from, to);
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(queryService.tenantDaily(p.getTenantId(), from, to), RequestId.get());
    }

    /**
     * 返回当前租户内某应用的 UTC 日报。
     *
     * <p>路由为 {@code GET /api/v1/stats/applications/{id}/overview}；应用 ID 只作为
     * 当前认证租户下的筛选条件，不能跨租户读取。
     *
     * @param applicationId 应用 ID
     * @param from 起始 UTC 日期，包含
     * @param to 结束 UTC 日期，包含
     * @return 应用日报列表
     */
    @GetMapping("/stats/applications/{id}/overview")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<AnalyticsQueryService.DailyStat>> applicationOverview(
            @PathVariable("id") long applicationId,
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to
    ) {
        validateDateRange(from, to);
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(queryService.applicationDaily(p.getTenantId(), applicationId, from, to), RequestId.get());
    }

    /**
     * 保留应用日报的兼容路由。
     *
     * <p>路由为 {@code GET /api/v1/applications/{id}/stats/overview}，语义与
     * {@link #applicationOverview(long, LocalDate, LocalDate)} 完全相同，不形成另一套统计
     * 口径或授权规则。
     *
     * @param applicationId 应用 ID
     * @param from 起始 UTC 日期，包含
     * @param to 结束 UTC 日期，包含
     * @return 应用日报列表
     */
    @GetMapping("/applications/{id}/stats/overview")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<AnalyticsQueryService.DailyStat>> applicationOverviewAlias(
            @PathVariable("id") long applicationId,
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to
    ) {
        return applicationOverview(applicationId, from, to);
    }

    /**
     * 返回当前租户内某域名的 UTC 日报。
     *
     * <p>路由为 {@code GET /api/v1/stats/domains/{id}/overview}。域名筛选由查询层连同
     * 认证主体的 tenantId 执行，空结果不代表可见其他租户的资源不存在。
     *
     * @param domainId 域名 ID
     * @param from 起始 UTC 日期，包含
     * @param to 结束 UTC 日期，包含
     * @return 域名日报列表
     */
    @GetMapping("/stats/domains/{id}/overview")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<AnalyticsQueryService.DailyStat>> domainOverview(
            @PathVariable("id") long domainId,
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to
    ) {
        validateDateRange(from, to);
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(queryService.domainDaily(p.getTenantId(), domainId, from, to), RequestId.get());
    }

    /**
     * 返回当前租户指定日期范围内的 Top 短链。
     *
     * <p>路由为 {@code GET /api/v1/stats/top-links}。{@code limit} 缺省为 10，取值范围为
     * 1 到 100；{@code sortBy} 缺省为 {@code pv}，只接受忽略大小写后的 {@code pv}/{@code uv}。
     * 多日 {@code uv} 是日 UV 的累加，跨日同一访客可能重复计数。
     *
     * @param from 起始 UTC 日期，包含
     * @param to 结束 UTC 日期，包含
     * @param limit 返回上限，缺省为 10
     * @param sortBy 排序指标，缺省为 {@code pv}
     * @return 按选定指标排序的短链统计
     */
    @GetMapping("/stats/top-links")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<AnalyticsQueryService.TopLinkStat>> topLinks(
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "sortBy", required = false) String sortBy
    ) {
        validateDateRange(from, to);
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
        return ApiResponse.ok(reportingService.topLinks(p.getTenantId(), from, to, l, s), RequestId.get());
    }

    /**
     * 返回当前租户内某应用的 Top 短链。
     *
     * <p>路由为 {@code GET /api/v1/stats/applications/{id}/top-links}，日期、排序和 limit
     * 约束与 {@link #topLinks(LocalDate, LocalDate, Integer, String)} 相同。返回展示摘要由
     * 报表应用服务补齐；短链已删除或不可再读取时会标记 {@code deleted=true}。
     *
     * @param applicationId 应用 ID
     * @param from 起始 UTC 日期，包含
     * @param to 结束 UTC 日期，包含
     * @param limit 返回上限，缺省为 10
     * @param sortBy 排序指标，缺省为 {@code pv}
     * @return 应用范围内的 Top 短链统计
     */
    @GetMapping("/stats/applications/{id}/top-links")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<AnalyticsQueryService.TopLinkStat>> applicationTopLinks(
            @PathVariable("id") long applicationId,
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "sortBy", required = false) String sortBy
    ) {
        AnalyticsQueryService.TopSortBy sort = resolveTopSortBy(from, to, limit, sortBy);
        int l = normalizeTopLimit(limit);
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(reportingService.applicationTopLinks(p.getTenantId(), applicationId, from, to, l, sort), RequestId.get());
    }

    /**
     * 保留应用 Top 短链的兼容路由。
     *
     * <p>路由为 {@code GET /api/v1/applications/{id}/stats/top-links}，直接复用
     * {@link #applicationTopLinks(long, LocalDate, LocalDate, Integer, String)} 的校验、
     * 租户范围与返回口径。
     *
     * @param applicationId 应用 ID
     * @param from 起始 UTC 日期，包含
     * @param to 结束 UTC 日期，包含
     * @param limit 返回上限，缺省为 10
     * @param sortBy 排序指标，缺省为 {@code pv}
     * @return 应用范围内的 Top 短链统计
     */
    @GetMapping("/applications/{id}/stats/top-links")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<AnalyticsQueryService.TopLinkStat>> applicationTopLinksAlias(
            @PathVariable("id") long applicationId,
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "sortBy", required = false) String sortBy
    ) {
        return applicationTopLinks(applicationId, from, to, limit, sortBy);
    }

    /**
     * 返回当前租户内某域名的 Top 短链。
     *
     * <p>路由为 {@code GET /api/v1/stats/domains/{id}/top-links}。排序参数与租户级
     * Top 短链一致，且域名 ID 不能绕过认证主体的 tenantId 范围。
     *
     * @param domainId 域名 ID
     * @param from 起始 UTC 日期，包含
     * @param to 结束 UTC 日期，包含
     * @param limit 返回上限，缺省为 10
     * @param sortBy 排序指标，缺省为 {@code pv}
     * @return 域名范围内的 Top 短链统计
     */
    @GetMapping("/stats/domains/{id}/top-links")
    @PreAuthorize("!hasRole('OPENAPI')")
    public ApiResponse<List<AnalyticsQueryService.TopLinkStat>> domainTopLinks(
            @PathVariable("id") long domainId,
            @RequestParam("from") @NotNull LocalDate from,
            @RequestParam("to") @NotNull LocalDate to,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "sortBy", required = false) String sortBy
    ) {
        AnalyticsQueryService.TopSortBy sort = resolveTopSortBy(from, to, limit, sortBy);
        int l = normalizeTopLimit(limit);
        AuthPrincipal p = AuthContext.requirePrincipal();
        return ApiResponse.ok(reportingService.domainTopLinks(p.getTenantId(), domainId, from, to, l, sort), RequestId.get());
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
        ReportRange.validate(from, to);
    }

}
