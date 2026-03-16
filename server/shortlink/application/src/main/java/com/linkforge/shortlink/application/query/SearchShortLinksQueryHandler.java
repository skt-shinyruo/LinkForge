package com.linkforge.shortlink.application.query;

import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.foundation.security.TenantGuard;
import com.linkforge.shortlink.application.ShortLinkService.LinkDto;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.OffsetPagingGuard;
import com.linkforge.shortlink.domain.ShortLink;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SearchShortLinksQueryHandler {

    private static final long MAX_SEARCH_OFFSET = 100_000L;

    private final ShortLinkRepository shortLinkRepository;
    private final LinkTagRepository linkTagRepository;
    private final ShortLinkDtoMapper dtoMapper;
    private final TenantGuard tenantGuard;

    public SearchShortLinksQueryHandler(
            ShortLinkRepository shortLinkRepository,
            LinkTagRepository linkTagRepository,
            ShortLinkDtoMapper dtoMapper,
            TenantGuard tenantGuard
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.linkTagRepository = linkTagRepository;
        this.dtoMapper = dtoMapper;
        this.tenantGuard = tenantGuard;
    }

    public PageResult<LinkDto> handle(long tenantId, ShortLinkSearchQuery query, PageQuery pageQuery) {
        tenantGuard.requireCurrentTenant(tenantId);
        long offset = OffsetPagingGuard.requireOffsetWithin(pageQuery, MAX_SEARCH_OFFSET);

        long total = shortLinkRepository.countSearch(tenantId, query);
        if (total <= 0) {
            return new PageResult<>(List.of(), 0, pageQuery.page(), pageQuery.size());
        }

        List<ShortLink> links = shortLinkRepository.listSearch(tenantId, query, offset, pageQuery.size());
        Map<Long, List<String>> tags = TagMaps.loadTagsByLinkIds(linkTagRepository, links);
        List<LinkDto> dtos = links.stream()
                .map(e -> dtoMapper.toDto(e, tags.getOrDefault(e.id(), List.of())))
                .toList();
        return new PageResult<>(dtos, total, pageQuery.page(), pageQuery.size());
    }
}

