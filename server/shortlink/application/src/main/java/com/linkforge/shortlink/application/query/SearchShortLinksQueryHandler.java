package com.linkforge.shortlink.application.query;

import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.shortlink.application.LinkDto;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.OffsetPagingGuard;
import com.linkforge.shortlink.domain.ShortLink;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 执行租户内短链搜索、分页和 DTO 聚合。
 *
 * <p>本 handler 只消费已经解析好的 {@link ShortLinkSearchQuery}，不认证主体，也不推导用户/API Key scope。
 * 上游必须先把应用归属或普通用户的 {@code createdBy + unscopedOnly} 限制写入 query；持久化查询本身始终附带
 * {@code tenantId}，这是最后的数据隔离边界。</p>
 *
 * <p>兼容路径按 offset 查询，可选择跳过 count；扩展路径使用 {@code created_at DESC, id DESC} keyset cursor，
 * 不随页深度增加扫描成本。只有本页短链会触发一次批量标签查询，避免逐行 N+1；标签表没有 tenant 列，因此不得向
 * {@link TagMaps} 传入未经租户隔离的聚合。</p>
 */
@Component
public class SearchShortLinksQueryHandler {

    private static final long MAX_SEARCH_OFFSET = 100_000L;

    private final ShortLinkRepository shortLinkRepository;
    private final LinkTagRepository linkTagRepository;
    private final ShortLinkDtoMapper dtoMapper;

    public SearchShortLinksQueryHandler(
            ShortLinkRepository shortLinkRepository,
            LinkTagRepository linkTagRepository,
            ShortLinkDtoMapper dtoMapper
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.linkTagRepository = linkTagRepository;
        this.dtoMapper = dtoMapper;
    }

    /**
     * 返回符合过滤条件的一页短链及 count 时刻的总数。
     *
     * <p>{@code query == null} 由仓储解释为“未归档、无其他过滤条件”。总数为零时跳过列表与标签查询；
     * 分页参数为空或 offset 超限时在访问仓储前返回参数错误。</p>
     *
     * @param tenantId 已授权主体所属租户
     * @param query 已应用主体 scope 的可选搜索条件
     * @param pageQuery 非空且已规范化页大小的分页参数
     * @return 当前页 DTO、总数以及原 page/size
     */
    public PageResult<LinkDto> handle(long tenantId, ShortLinkSearchQuery query, PageQuery pageQuery) {
        return handle(tenantId, query, pageQuery, true, null);
    }

    /**
     * 执行可选总数与可选 keyset cursor 的搜索。游标存在时 page 仅作为响应兼容字段，不参与 SQL。
     */
    public PageResult<LinkDto> handle(
            long tenantId,
            ShortLinkSearchQuery query,
            PageQuery pageQuery,
            boolean includeTotal,
            String cursor
    ) {
        ShortLinkSearchCursorCodec.Cursor decodedCursor = ShortLinkSearchCursorCodec.decode(cursor);
        long offset = decodedCursor == null
                ? OffsetPagingGuard.requireOffsetWithin(pageQuery, MAX_SEARCH_OFFSET)
                : 0L;

        long total = includeTotal ? shortLinkRepository.countSearch(tenantId, query) : -1L;
        if (includeTotal && total <= 0) {
            return new PageResult<>(List.of(), 0, pageQuery.page(), pageQuery.size(), false, null);
        }

        int fetchLimit = pageQuery.size() == Integer.MAX_VALUE ? Integer.MAX_VALUE : pageQuery.size() + 1;
        List<ShortLink> fetched = decodedCursor == null
                ? shortLinkRepository.listSearch(tenantId, query, offset, includeTotal ? pageQuery.size() : fetchLimit)
                : shortLinkRepository.listSearchAfter(
                        tenantId,
                        query,
                        decodedCursor.createdAtUtc(),
                        decodedCursor.id(),
                        fetchLimit
                );
        boolean hasMore = includeTotal && decodedCursor == null
                ? offset + fetched.size() < total
                : fetched.size() > pageQuery.size();
        List<ShortLink> links = hasMore && fetched.size() > pageQuery.size()
                ? List.copyOf(fetched.subList(0, pageQuery.size()))
                : List.copyOf(fetched);

        Map<Long, List<String>> tags = TagMaps.loadTagsByLinkIds(linkTagRepository, links);
        List<LinkDto> dtos = links.stream()
                .map(e -> dtoMapper.toDto(e, tags.getOrDefault(e.id(), List.of())))
                .toList();
        ShortLink last = links.isEmpty() ? null : links.get(links.size() - 1);
        String nextCursor = hasMore && last != null
                ? ShortLinkSearchCursorCodec.encode(last.createdAtUtc(), last.id())
                : null;
        return new PageResult<>(dtos, total, pageQuery.page(), pageQuery.size(), hasMore, nextCursor);
    }
}
