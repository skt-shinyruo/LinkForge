package com.linkforge.analytics.infrastructure.catalog;

import org.apache.ibatis.annotations.Mapper;

/**
 * Analytics 链接目录的幂等 upsert mapper。
 *
 * <p>目录由 ShortLink integration event 投影维护，是应用/域范围报表与点击额度 SQL 的归属事实来源；
 * 它不是重定向权威读模型。</p>
 */
@Mapper
public interface AnalyticsLinkCatalogMapper {

    /** 按 {@code tenant_id + link_id} 写入或更新投影快照。 */
    int upsert(AnalyticsLinkCatalogRow row);
}
