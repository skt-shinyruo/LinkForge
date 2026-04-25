package com.linkforge.analytics.infrastructure.catalog;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnalyticsLinkCatalogMapper {
    int upsert(AnalyticsLinkCatalogRow row);
}
