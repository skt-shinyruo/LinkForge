package com.linkforge.analytics.interfaces.web;

import com.linkforge.analytics.application.AnalyticsQueryService;

/**
 * 将 Analytics 应用层读模型转换为稳定的 HTTP 响应形状。
 *
 * <p>该适配器刻意只做字段投影，不在 Web 层重新计算 PV、UV、比例或资源归属。统计口径、
 * 删除标记和敏感字段访问控制分别属于应用服务及其调用入口；在此处加入默认值或脱敏会让
 * HTTP 结果与已发布的应用契约产生双份事实源。
 */
final class AnalyticsHttpMapper {

    private AnalyticsHttpMapper() {
    }

    /**
     * 投影按日聚合结果，保留其 UTC 日期和 HLL 近似 UV 数值。
     *
     * @param stat 应用层返回的单日统计
     * @return HTTP 日报响应
     */
    static DailyStatHttpResponse toDailyStatResponse(AnalyticsQueryService.DailyStat stat) {
        return new DailyStatHttpResponse(stat.day(), stat.pv(), stat.uv());
    }

    /**
     * 投影 Top 短链统计，不把 {@code deleted} 资源重新解释为可跳转的短链。
     *
     * @param stat 已补齐摘要的 Top 短链统计
     * @return HTTP Top 短链响应
     */
    static TopLinkStatHttpResponse toTopLinkStatResponse(AnalyticsQueryService.TopLinkStat stat) {
        return new TopLinkStatHttpResponse(
                stat.linkId(),
                stat.code(),
                stat.shortUrl(),
                stat.originalUrl(),
                stat.pv(),
                stat.uv(),
                stat.deleted()
        );
    }

    /**
     * 投影单个维度值的统计，比例直接来自查询服务的同类维度 PV 分母。
     *
     * @param stat 应用层返回的维度统计
     * @return HTTP 维度统计响应
     */
    static DimensionStatHttpResponse toDimensionStatResponse(AnalyticsQueryService.DimensionStat stat) {
        return new DimensionStatHttpResponse(stat.value(), stat.pv(), stat.uv(), stat.ratio());
    }

    /**
     * 投影已授权访问明细。
     *
     * <p>该方法不对 {@code ipHash}、原始 User-Agent 或 UTM 字段再做脱敏；调用方必须已经
     * 经过 {@link StatsController#linkEvents(long, java.time.LocalDateTime, java.time.LocalDateTime, Integer)}
     * 的管理员授权路径，且不得把返回对象记录到非受控日志。
     *
     * @param event 应用层返回的受控访问明细
     * @return HTTP 访问明细响应
     */
    static VisitEventHttpResponse toVisitEventResponse(AnalyticsQueryService.VisitEvent event) {
        return new VisitEventHttpResponse(
                event.occurredAt(),
                event.requestId(),
                event.ipHash(),
                event.userAgentRaw(),
                event.userAgentFamily(),
                event.osFamily(),
                event.deviceType(),
                event.refererDomain(),
                event.language(),
                event.utmSource(),
                event.utmMedium(),
                event.utmCampaign()
        );
    }
}
