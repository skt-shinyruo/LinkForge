package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.runtime.security.TenantGuard;
import com.linkforge.foundation.tx.AfterCommit;
import com.linkforge.shortlink.application.ShortLinkService.LinkDto;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.domain.ShortLink;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Component
public class RestoreShortLinkCommandHandler {

    private final ShortLinkRepository shortLinkRepository;
    private final ShortLinkEventPublisher eventPublisher;
    private final LinkTagRepository linkTagRepository;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final ShortLinkDtoMapper dtoMapper;
    private final TenantGuard tenantGuard;
    private final Clock clock;

    public RestoreShortLinkCommandHandler(
            ShortLinkRepository shortLinkRepository,
            ShortLinkEventPublisher eventPublisher,
            LinkTagRepository linkTagRepository,
            RedirectCacheSyncPort redirectCacheSync,
            ShortLinkDtoMapper dtoMapper,
            TenantGuard tenantGuard,
            Clock clock
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.eventPublisher = eventPublisher;
        this.linkTagRepository = linkTagRepository;
        this.redirectCacheSync = redirectCacheSync;
        this.dtoMapper = dtoMapper;
        this.tenantGuard = tenantGuard;
        this.clock = clock;
    }

    @Transactional
    public LinkDto handle(long tenantId, long linkId) {
        tenantGuard.requireCurrentTenant(tenantId);
        ShortLink link = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND));

        boolean restored = false;
        if (link.archivedAtUtc() != null) {
            link.restore();
            if (!shortLinkRepository.update(link)) {
                throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE);
            }
            link.incrementVersion();
            restored = true;
        }

        if (restored) {
            eventPublisher.restored(link, clock.instant());
            AfterCommit.run(() -> redirectCacheSync.evict(link.tenantId(), link.domainId(), link.code().value()));
        }

        List<String> tags = linkTagRepository.findTagNamesByLinkId(linkId);
        return dtoMapper.toDto(link, tags);
    }
}
