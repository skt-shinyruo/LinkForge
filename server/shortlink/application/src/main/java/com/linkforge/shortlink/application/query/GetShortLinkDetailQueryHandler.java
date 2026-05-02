package com.linkforge.shortlink.application.query;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.shortlink.application.LinkDto;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.domain.ShortLink;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetShortLinkDetailQueryHandler {

    private final ShortLinkRepository shortLinkRepository;
    private final LinkTagRepository linkTagRepository;
    private final ShortLinkDtoMapper dtoMapper;

    public GetShortLinkDetailQueryHandler(
            ShortLinkRepository shortLinkRepository,
            LinkTagRepository linkTagRepository,
            ShortLinkDtoMapper dtoMapper
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.linkTagRepository = linkTagRepository;
        this.dtoMapper = dtoMapper;
    }

    public LinkDto handle(long tenantId, long linkId) {
        ShortLink link = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND));
        List<String> tags = linkTagRepository.findTagNamesByLinkId(linkId);
        return dtoMapper.toDto(link, tags);
    }
}
