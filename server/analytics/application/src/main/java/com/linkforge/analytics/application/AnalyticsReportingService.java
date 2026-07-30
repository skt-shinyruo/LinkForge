package com.linkforge.analytics.application;

import java.time.LocalDate;
import java.util.List;

/**
 * 对外暴露已补齐短链摘要的 Top 链接报表契约。
 *
 * <p>调用方负责日期和 {@code limit} 参数校验；实现必须保持 tenantId 作为每次查询和摘要补全的隔离条件。返回的
 * UV 是当前读模型的近似/聚合值，不是跨日精确 UV。</p>
 */
public interface AnalyticsReportingService {

    /** 查询租户范围的 Top 链接并补齐短链展示信息。 */
    List<AnalyticsQueryService.TopLinkStat> topLinks(
            long tenantId,
            LocalDate from,
            LocalDate to,
            int limit,
            AnalyticsQueryService.TopSortBy sortBy
    );

    /** 查询应用范围的 Top 链接并补齐短链展示信息。 */
    List<AnalyticsQueryService.TopLinkStat> applicationTopLinks(
            long tenantId,
            long applicationId,
            LocalDate from,
            LocalDate to,
            int limit,
            AnalyticsQueryService.TopSortBy sortBy
    );

    /** 查询域名范围的 Top 链接并补齐短链展示信息。 */
    List<AnalyticsQueryService.TopLinkStat> domainTopLinks(
            long tenantId,
            long domainId,
            LocalDate from,
            LocalDate to,
            int limit,
            AnalyticsQueryService.TopSortBy sortBy
    );
}
