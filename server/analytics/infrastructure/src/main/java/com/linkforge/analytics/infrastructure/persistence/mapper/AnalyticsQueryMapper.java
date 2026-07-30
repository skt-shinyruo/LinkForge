package com.linkforge.analytics.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报表读取 SQL 的 MyBatis 映射。
 *
 * <p>日统计范围使用闭区间，应用额度统计单独使用 {@code [fromInclusiveUtc, toExclusiveUtc)}。租户、
 * 应用、域的日 UV 查询优先使用 {@code analytics_scope_stats_daily} 中的范围 HLL 快照，缺失时回退到
 * 链接日 UV 求和；后者并非跨链接精确去重。</p>
 */
@Mapper
public interface AnalyticsQueryMapper {

    List<AnalyticsDailyStatRow> linkDaily(long tenantId, long linkId, LocalDate from, LocalDate to);

    List<AnalyticsDailyStatRow> tenantDaily(long tenantId, LocalDate from, LocalDate to);

    List<AnalyticsDailyStatRow> applicationDaily(long tenantId, long applicationId, LocalDate from, LocalDate to);

    List<AnalyticsDailyStatRow> domainDaily(long tenantId, long domainId, LocalDate from, LocalDate to);

    /** 查询月度额度初始化使用的应用 PV，结束日期为排他边界。 */
    Long countApplicationPv(
            @Param("tenantId") long tenantId,
            @Param("applicationId") long applicationId,
            @Param("fromInclusiveUtc") LocalDate fromInclusiveUtc,
            @Param("toExclusiveUtc") LocalDate toExclusiveUtc
    );

    List<AnalyticsTopLinkRow> topLinksOrderByPv(long tenantId, LocalDate from, LocalDate to, int limit);

    List<AnalyticsTopLinkRow> topLinksOrderByUv(long tenantId, LocalDate from, LocalDate to, int limit);

    List<AnalyticsTopLinkRow> applicationTopLinksOrderByPv(long tenantId, long applicationId, LocalDate from, LocalDate to, int limit);

    List<AnalyticsTopLinkRow> applicationTopLinksOrderByUv(long tenantId, long applicationId, LocalDate from, LocalDate to, int limit);

    List<AnalyticsTopLinkRow> domainTopLinksOrderByPv(long tenantId, long domainId, LocalDate from, LocalDate to, int limit);

    List<AnalyticsTopLinkRow> domainTopLinksOrderByUv(long tenantId, long domainId, LocalDate from, LocalDate to, int limit);

    Long linkDimTotalPv(long tenantId, long linkId, LocalDate from, LocalDate to, String dimType);

    List<AnalyticsDimensionRow> linkDimRows(long tenantId, long linkId, LocalDate from, LocalDate to, String dimType, int limit);

    List<AnalyticsVisitEventRow> linkEvents(long tenantId, long linkId, LocalDateTime from, LocalDateTime to, int limit);

}
