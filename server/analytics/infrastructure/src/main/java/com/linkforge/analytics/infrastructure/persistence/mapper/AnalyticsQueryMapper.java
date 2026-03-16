package com.linkforge.analytics.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AnalyticsQueryMapper {

    List<AnalyticsDailyStatRow> linkDaily(long tenantId, long linkId, LocalDate from, LocalDate to);

    List<AnalyticsDailyStatRow> tenantDaily(long tenantId, LocalDate from, LocalDate to);

    List<AnalyticsTopLinkRow> topLinksOrderByPv(long tenantId, LocalDate from, LocalDate to, int limit);

    List<AnalyticsTopLinkRow> topLinksOrderByUv(long tenantId, LocalDate from, LocalDate to, int limit);

    Long linkDimTotalPv(long tenantId, long linkId, LocalDate from, LocalDate to, String dimType);

    List<AnalyticsDimensionRow> linkDimRows(long tenantId, long linkId, LocalDate from, LocalDate to, String dimType, int limit);

    List<AnalyticsVisitEventRow> linkEvents(long tenantId, long linkId, LocalDateTime from, LocalDateTime to, int limit);
}
