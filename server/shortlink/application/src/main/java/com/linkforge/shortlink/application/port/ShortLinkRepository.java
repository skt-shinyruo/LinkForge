package com.linkforge.shortlink.application.port;

import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import com.linkforge.shortlink.domain.ShortLink;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 短链聚合的写仓储及命令侧受控查询端口。
 *
 * <p>显式接收 {@code tenantId} 的方法必须把租户条件下推到数据库，不能先按 ID 读取后再在内存中过滤。
 * 按域名查询以全局唯一的 domainId 为 scope，调用方须先验证域名归属；无 scope 查询只服务历史兼容，
 * 两者都不能单独作为租户授权依据。返回 {@link Optional} 或集合的方法不得返回 {@code null}。</p>
 *
 * <p>{@link #update(ShortLink)} 与 {@link #deleteByTenantIdAndId(long, long, long)} 使用聚合版本号做
 * 乐观并发控制。布尔返回值只表达 CAS 是否命中，不区分记录不存在与版本冲突；应用层负责将失败翻译为
 * 稳定业务错误。</p>
 */
public interface ShortLinkRepository {

    /**
     * 按租户和短链 ID 读取聚合，包括已归档记录。
     *
     * @return 找到时返回聚合，否则返回 {@link Optional#empty()}，永不返回 {@code null}
     */
    Optional<ShortLink> findByTenantIdAndId(long tenantId, long linkId);

    /**
     * 按短码读取 {@code domain_id IS NULL} 的历史无 scope 短链。
     *
     * <p>该查询没有租户条件，只能用于无 scope 短码唯一性检查等内部流程，结果本身不能作为租户授权依据。
     * {@code code} 为空或不存在时返回空值。</p>
     */
    Optional<ShortLink> findUnscopedByCode(String code);

    /**
     * 在指定域名 scope 内按短码读取聚合。
     *
     * <p>域名 ID 与短码共同构成查询边界；调用方仍须在进入本端口前确认域名属于当前租户。</p>
     *
     * @return 找到时返回聚合，否则返回空 {@link Optional}
     */
    Optional<ShortLink> findByDomainIdAndCode(long domainId, String code);

    /**
     * 统计租户内指定应用在 UTC 半开区间内创建的短链数量。
     *
     * <p>区间语义固定为 {@code [fromInclusiveUtc, toExclusiveUtc)}，相邻月份可以无重叠拼接；非法 ID、
     * 空时间或非递增区间返回 {@code 0}。本查询只是计数，不提供并发预占保证。</p>
     */
    long countCreatedByTenantIdAndApplicationIdAndCreatedAtRange(
            long tenantId,
            long applicationId,
            LocalDateTime fromInclusiveUtc,
            LocalDateTime toExclusiveUtc
    );

    /**
     * 插入一个新聚合。
     *
     * <p>实现应完整保存聚合当前版本和 scope 字段。短码唯一键等数据约束冲突必须向上抛出，不能转换为
     * 幂等成功；事务边界由应用服务提供。</p>
     *
     * @param link 非空的新短链聚合
     */
    void insert(ShortLink link);

    /**
     * 按租户、ID 和聚合当前版本执行 CAS 更新，并在数据库中原子递增版本号。
     *
     * <p>成功后持久化版本已经加一，但传入聚合的内存版本仍由调用方负责在确认成功后推进；失败时不得推进
     * 内存版本，也不得覆盖并发写入。</p>
     *
     * @return 命中当前版本并完成更新时返回 {@code true}；记录不存在或版本已变化时返回 {@code false}
     */
    boolean update(ShortLink link);

    /**
     * 按租户、短链 ID 和版本物理删除记录。
     *
     * <p>生命周期是否允许删除由聚合和应用层先行校验；本方法只执行版本受控的持久化删除。</p>
     *
     * @return 删除一行时返回 {@code true}；不存在或发生版本冲突时返回 {@code false}
     */
    boolean deleteByTenantIdAndId(long tenantId, long linkId, long version);

    /**
     * 使用租户隔离的搜索条件统计结果总数。
     *
     * <p>{@code query == null} 表示默认的未归档列表条件。计数条件必须与
     * {@link #listSearch(long, ShortLinkSearchQuery, long, int)} 保持一致。</p>
     */
    long countSearch(long tenantId, ShortLinkSearchQuery query);

    /**
     * 使用租户隔离的搜索条件读取一页聚合。
     *
     * <p>调用方负责在进入仓储前限制 {@code offset} 与 {@code limit}；实现应提供稳定排序。没有结果时
     * 返回空列表，不返回 {@code null}。</p>
     */
    List<ShortLink> listSearch(long tenantId, ShortLinkSearchQuery query, long offset, int limit);

    /**
     * 按 {@code created_at DESC, id DESC} 从游标之后读取结果，避免数据库扫描并丢弃深 offset。
     *
     * <p>生产持久化 adapter 必须实现本方法；默认实现用于让不支持游标的测试替身显式失败。</p>
     */
    default List<ShortLink> listSearchAfter(
            long tenantId,
            ShortLinkSearchQuery query,
            LocalDateTime cursorCreatedAtUtc,
            long cursorId,
            int limit
    ) {
        throw new UnsupportedOperationException("cursor pagination is not supported by this repository adapter");
    }
}
