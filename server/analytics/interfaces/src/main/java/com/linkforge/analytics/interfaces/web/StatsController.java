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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 统计报表与访问明细审批的 HTTP 边界。
 *
 * <p>所有端点都从 {@link AuthContext} 取得已认证主体的 tenantId，绝不接受客户端提供的租户
 * 参数；查询、排序和资源归属因此始终在当前租户范围内执行。{@code OPENAPI} 角色不能访问这些
 * 管理端报表端点，避免 API Key 意外获得统计明细能力。
 *
 * <p>日报参数使用 UTC {@link LocalDate}，首尾日期均包含。访问明细与导出申请的
 * {@link LocalDateTime} 不携带时区，也按 UTC 解释。这里仅处理 HTTP 可验证的公共边界；
 * 明细管理员权限、默认时间窗和短链归属校验仍由应用服务统一实施。所有成功响应携带当前
 * {@link RequestId}，便于与异步统计链路的日志关联。
 */
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
    public ApiResponse<List<DailyStatHttpResponse>> applicationOverviewAlias(
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
    public ApiResponse<List<TopLinkStatHttpResponse>> applicationTopLinksAlias(
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

    /**
     * 返回短链指定维度的聚合统计。
     *
     * <p>路由为 {@code GET /api/v1/stats/links/{id}/dimensions}。{@code type} 会去除首尾
     * 空白并转为小写，只允许 {@code referer_domain}、{@code language}、{@code ua_family}、
     * {@code os_family}、{@code device_type}、{@code utm_source}、{@code utm_medium} 和
     * {@code utm_campaign}。{@code limit} 缺省为 10，范围为 1 到 100；日期范围与日报一致。
     *
     * <p>响应中的 {@code ratio} 是当前 type、当前日期范围下 value PV 占该 type 总 PV 的比例，
     * 不是全量链接 PV 占比。多日 UV 仍是日 UV 的累加，不是区间精确去重。
     *
     * @param linkId 当前租户内的短链 ID
     * @param from 起始 UTC 日期，包含
     * @param to 结束 UTC 日期，包含
     * @param type 维度类型
     * @param limit 返回上限，缺省为 10
     * @return 指定维度的统计列表
     */
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

    /**
     * 查询短链访问明细，而不是聚合报表。
     *
     * <p>路由为 {@code GET /api/v1/stats/links/{id}/events}。该接口将主体映射为
     * {@link UserActor}，应用服务要求租户管理员或平台管理员；普通已认证用户会得到
     * {@code FORBIDDEN}。时间值按 UTC 解释，缺省范围为当前 UTC 时刻往前一天，最长 7 天；
     * {@code limit} 缺省为 50，范围为 1 到 200。
     *
     * <p>结果含 IP 哈希、原始 User-Agent 和 UTM 等可能具有关联性的字段，只应在受控管理
     * 页面展示，不得写入普通访问日志或作为身份认证依据。
     *
     * @param linkId 当前租户内的短链 ID
     * @param from 可选的起始 UTC 时间
     * @param to 可选的结束 UTC 时间
     * @param limit 可选的返回上限
     * @return 经管理员授权后的访问明细
     */
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

    /**
     * 为短链访问明细创建导出审批申请。
     *
     * <p>路由为 {@code POST /api/v1/stats/links/{id}/events/export-requests}。此操作只创建
     * {@code ANALYTICS_DETAIL_EXPORT} 审批记录，不会返回文件、下载地址或访问明细。应用服务
     * 校验短链属于当前租户，默认申请最近一个 UTC 日，并拒绝 {@code from > to}。
     *
     * @param linkId 当前租户内的短链 ID
     * @param from 可选的起始 UTC 时间
     * @param to 可选的结束 UTC 时间
     * @return 新建或返回的审批申请视图
     */
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

    /**
     * 为指定应用下短链的访问明细创建导出审批申请。
     *
     * <p>路由为 {@code POST /api/v1/applications/{applicationId}/links/{id}/events/export-requests}。
     * 除短链租户归属外，应用服务还要求该短链确实属于路径中的 applicationId；不匹配时返回
     * {@code FORBIDDEN}。该接口同样只提交审批，审批通过后的文件生成不属于此 HTTP 操作。
     *
     * @param applicationId 期望所属的应用 ID
     * @param linkId 当前租户内的短链 ID
     * @param from 可选的起始 UTC 时间
     * @param to 可选的结束 UTC 时间
     * @return 新建或返回的审批申请视图
     */
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
