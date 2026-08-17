package com.linkforge.analytics.application;

import java.time.LocalDate;
import java.util.List;

/**
 * Analytics 已落库读模型的查询契约。
 *
 * <p>所有方法都要求调用方显式传入 tenantId，适配器不得把资源 ID 当作跨租户全局查询条件。日统计的
 * {@code from}/{@code to} 均为 UTC 日期且包含两端。</p>
 *
 * <p>标准事件的 Stream 重放由 requestId 幂等投影保护；历史无 requestId 消息或调用方重复生成事件仍可能影响
 * PV。UV 来自 HyperLogLog 或其持久化快照，多日 UV 目前是日 UV 之和，不能解释为区间精确去重人数。
 * 空列表表示该读模型中没有匹配行，不承诺资源存在性。</p>
 */
public interface AnalyticsQueryService {

    /** 查询一条短链按日聚合的 PV/UV。 */
    List<DailyStat> linkDaily(long tenantId, long linkId, LocalDate from, LocalDate to);

    /** 查询整个租户按日聚合的 PV/UV。 */
    List<DailyStat> tenantDaily(long tenantId, LocalDate from, LocalDate to);

    /** 查询一个应用按日聚合的 PV/UV。 */
    List<DailyStat> applicationDaily(long tenantId, long applicationId, LocalDate from, LocalDate to);

    /** 查询一个域名按日聚合的 PV/UV。 */
    List<DailyStat> domainDaily(long tenantId, long domainId, LocalDate from, LocalDate to);

    /**
     * 按 PV 查询租户 Top 链接的兼容便捷入口。
     *
     * <p>等价于显式传入 {@link TopSortBy#PV}。</p>
     */
    List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit);

    /** 按指定指标查询租户 Top 链接；{@code limit} 的输入校验由上层接口负责。 */
    List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy);

    /** 按指定指标查询一个应用范围内的 Top 链接。 */
    List<TopLinkStat> applicationTopLinks(long tenantId, long applicationId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy);

    /** 按指定指标查询一个域名范围内的 Top 链接。 */
    List<TopLinkStat> domainTopLinks(long tenantId, long domainId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy);

    /** 单日统计快照；{@code day} 按 UTC 切分。 */
    record DailyStat(LocalDate day, long pv, long uv) {
    }

    /** Top 链接排序指标。 */
    enum TopSortBy {
        PV,
        UV
    }

    /**
     * Top 链接统计快照。
     *
     * <p>{@code code}/{@code shortUrl}/{@code originalUrl} 可来自延迟的 catalog 投影或后续补全；
     * {@code deleted=true} 表示链接已删除或补全时已不可见，统计值仍是历史事实。</p>
     */
    record TopLinkStat(long linkId, String code, String shortUrl, String originalUrl, long pv, long uv, boolean deleted) {
        /** 保留旧查询实现使用的无 shortUrl 构造形式。 */
        public TopLinkStat(long linkId, String code, String originalUrl, long pv, long uv, boolean deleted) {
            this(linkId, code, null, originalUrl, pv, uv, deleted);
        }
    }

}
