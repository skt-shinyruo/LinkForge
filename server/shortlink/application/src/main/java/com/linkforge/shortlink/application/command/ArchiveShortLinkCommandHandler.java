package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.security.TenantGuard;
import com.linkforge.shortlink.application.ShortLinkService.LinkDto;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.ShortLinkDomainExceptions;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkDomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class ArchiveShortLinkCommandHandler {

    private final ShortLinkRepository shortLinkRepository;
    private final ShortLinkEventPublisher eventPublisher;
    private final LinkTagRepository linkTagRepository;
    private final ShortLinkDtoMapper dtoMapper;
    private final TenantGuard tenantGuard;
    private final Clock clock;

    public ArchiveShortLinkCommandHandler(
            ShortLinkRepository shortLinkRepository,
            ShortLinkEventPublisher eventPublisher,
            LinkTagRepository linkTagRepository,
            ShortLinkDtoMapper dtoMapper,
            TenantGuard tenantGuard,
            Clock clock
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.eventPublisher = eventPublisher;
        this.linkTagRepository = linkTagRepository;
        this.dtoMapper = dtoMapper;
        this.tenantGuard = tenantGuard;
        this.clock = clock;
    }

    @Transactional
    public LinkDto handle(long tenantId, long linkId) {
        tenantGuard.requireCurrentTenant(tenantId);
        ShortLink link = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND));

        boolean alreadyArchived = link.archivedAtUtc() != null;
        if (!alreadyArchived) {
            LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            try {
                link.archive(nowUtc);
            } catch (ShortLinkDomainException ex) {
                throw ShortLinkDomainExceptions.translate(ex);
            }
            shortLinkRepository.update(link);
            Instant occurredAtUtc = nowUtc.toInstant(ZoneOffset.UTC);
            eventPublisher.archived(link, occurredAtUtc);
        }

        List<String> tags = linkTagRepository.findTagNamesByLinkId(linkId);
        return dtoMapper.toDto(link, tags);
    }
}

