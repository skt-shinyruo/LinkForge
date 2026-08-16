package com.linkforge.shortlink.infrastructure.persistence.repository;

import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkCommandMapper;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkEntityMapper;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkQueryMapper;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkSearchParam;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 短链聚合的 MyBatis 写仓储，同时提供命令处理所需的受控查询。
 *
 * <p>所有租户内读写均把 {@code tenantId} 下推到 SQL；按 code 的查询则依赖 domain scope
 * 区分全局旧短链与自定义域名短链。更新和删除采用聚合版本号进行乐观并发控制，本适配器以
 * 布尔值暴露是否命中当前版本，冲突解释由应用层负责。</p>
 */
@Repository
public class MybatisShortLinkRepository implements ShortLinkRepository {

    private final ShortLinkCommandMapper commandMapper;
    private final ShortLinkQueryMapper queryMapper;

    public MybatisShortLinkRepository(
            ShortLinkCommandMapper commandMapper,
            ShortLinkQueryMapper queryMapper
    ) {
        this.commandMapper = commandMapper;
        this.queryMapper = queryMapper;
    }

    @Override
    public Optional<ShortLink> findByTenantIdAndId(long tenantId, long linkId) {
        if (tenantId <= 0 || linkId <= 0) {
            return Optional.empty();
        }
        ShortLinkEntity e = queryMapper.findByTenantIdAndId(tenantId, linkId);
        return Optional.ofNullable(ShortLinkEntityMapper.toDomain(e));
    }

    /**
     * 查找没有 domain scope 的旧短链。
     *
     * <p>该查询不包含租户条件，只能用于 code 在此 scope 下具备唯一性的内部流程；调用方不得
     * 将它当作租户授权依据。空白 code 不访问数据库。</p>
     */
    @Override
    public Optional<ShortLink> findUnscopedByCode(String code) {
        String c = normalizeNullable(code);
        if (c == null) {
            return Optional.empty();
        }
        ShortLinkEntity e = queryMapper.findUnscopedByCode(c);
        return Optional.ofNullable(ShortLinkEntityMapper.toDomain(e));
    }

    @Override
    public Optional<ShortLink> findByDomainIdAndCode(long domainId, String code) {
        String c = normalizeNullable(code);
        if (domainId <= 0 || c == null) {
            return Optional.empty();
        }
        ShortLinkEntity e = queryMapper.findByDomainIdAndCode(domainId, c);
        return Optional.ofNullable(ShortLinkEntityMapper.toDomain(e));
    }

    /**
     * 统计某租户、应用在 UTC 半开区间 {@code [fromInclusiveUtc, toExclusiveUtc)} 内创建的短链数。
     *
     * <p>非法 ID、空时间或非递增区间直接返回 {@code 0}。半开区间允许相邻统计窗口无重叠地拼接，
     * 调用方可据此建立配额基线或执行区间审计。</p>
     */
    @Override
    public long countCreatedByTenantIdAndApplicationIdAndCreatedAtRange(
            long tenantId,
            long applicationId,
            LocalDateTime fromInclusiveUtc,
            LocalDateTime toExclusiveUtc
    ) {
        if (tenantId <= 0 || applicationId <= 0 || fromInclusiveUtc == null || toExclusiveUtc == null
                || !toExclusiveUtc.isAfter(fromInclusiveUtc)) {
            return 0L;
        }
        return queryMapper.countCreatedByTenantIdAndApplicationIdAndCreatedAtRange(
                tenantId,
                applicationId,
                fromInclusiveUtc,
                toExclusiveUtc
        );
    }

    @Override
    public void insert(ShortLink link) {
        commandMapper.insert(ShortLinkEntityMapper.toEntity(link));
    }

    /**
     * 保存聚合已经推进的新版本，并用其前一版本执行 CAS。
     *
     * @return 命中租户、ID 和变化前版本时返回 {@code true}；不存在或并发版本冲突时返回 {@code false}
     */
    @Override
    public boolean update(ShortLink link) {
        return commandMapper.update(ShortLinkEntityMapper.toEntity(link)) > 0;
    }

    /**
     * 按租户、ID 和版本物理删除短链。
     *
     * <p>归档是独立的生命周期操作；进入本方法意味着执行真正的行删除。版本条件防止删除覆盖并发更新，
     * 返回 {@code false} 时由上层区分不存在与乐观锁冲突。</p>
     */
    @Override
    public boolean delete(ShortLink link) {
        if (link == null || link.version() <= 0) {
            return false;
        }
        return commandMapper.deleteByTenantIdAndIdAndVersion(
                link.tenantId(),
                link.id(),
                link.version() - 1
        ) > 0;
    }

    /**
     * 使用与列表查询完全相同的过滤条件统计总数。
     *
     * <p>{@code query == null} 等价于查询未归档短链；keyword 与 tag 会先去除首尾空白，空白值视为
     * 未设置。这里传入的分页参数仅用于复用参数对象，不参与 count SQL。</p>
     */
    @Override
    public long countSearch(long tenantId, ShortLinkSearchQuery query) {
        ShortLinkSearchParam param = toSearchParam(tenantId, query, 0, 1, null, null);
        return queryMapper.countSearch(param);
    }

    /**
     * 按搜索条件读取一页短链。
     *
     * <p>SQL 始终带租户条件，并按 {@code created_at DESC, id DESC} 稳定排序；{@code offset} 和
     * {@code limit} 原样交给 mapper，合法范围由应用层分页校验负责。空结果统一为不可变空列表。</p>
     */
    @Override
    public List<ShortLink> listSearch(long tenantId, ShortLinkSearchQuery query, long offset, int limit) {
        ShortLinkSearchParam param = toSearchParam(tenantId, query, offset, limit, null, null);
        List<ShortLinkEntity> rows = queryMapper.listSearch(param);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(ShortLinkEntityMapper::toDomain).toList();
    }

    @Override
    public List<ShortLink> listSearchAfter(
            long tenantId,
            ShortLinkSearchQuery query,
            LocalDateTime cursorCreatedAtUtc,
            long cursorId,
            int limit
    ) {
        ShortLinkSearchParam param = toSearchParam(
                tenantId,
                query,
                0L,
                limit,
                cursorCreatedAtUtc,
                cursorId
        );
        List<ShortLinkEntity> rows = queryMapper.listSearchAfter(param);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(ShortLinkEntityMapper::toDomain).toList();
    }

    private static ShortLinkSearchParam toSearchParam(
            long tenantId,
            ShortLinkSearchQuery query,
            long offset,
            int limit,
            LocalDateTime cursorCreatedAtUtc,
            Long cursorId
    ) {
        ShortLinkSearchQuery q = query == null ? new ShortLinkSearchQuery(false, null, null, null, null) : query;
        return new ShortLinkSearchParam(
                tenantId,
                q.archived(),
                q.enabled(),
                normalizeNullable(q.keyword()),
                normalizeNullable(q.tag()),
                q.applicationId(),
                q.createdBy(),
                q.createdByType() == null ? null : q.createdByType().name(),
                q.unscopedOnly(),
                offset,
                limit,
                cursorCreatedAtUtc,
                cursorId
        );
    }

    private static String normalizeNullable(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}
