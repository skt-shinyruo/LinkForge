package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.runtime.security.TenantGuard;
import com.linkforge.foundation.tx.AfterCommit;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.ShortLinkDomainExceptions;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkDomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
public class DeleteShortLinkCommandHandler {

    private final ShortLinkRepository shortLinkRepository;
    private final LinkTagRepository linkTagRepository;
    private final ShortLinkEventPublisher eventPublisher;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final TenantGuard tenantGuard;
    private final Clock clock;

    public DeleteShortLinkCommandHandler(
            ShortLinkRepository shortLinkRepository,
            LinkTagRepository linkTagRepository,
            ShortLinkEventPublisher eventPublisher,
            RedirectCacheSyncPort redirectCacheSync,
            TenantGuard tenantGuard,
            Clock clock
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.linkTagRepository = linkTagRepository;
        this.eventPublisher = eventPublisher;
        this.redirectCacheSync = redirectCacheSync;
        this.tenantGuard = tenantGuard;
        this.clock = clock;
    }

    @Transactional
    public void handle(long tenantId, long linkId) {
        tenantGuard.requireCurrentTenant(tenantId);
        ShortLink link = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND));

        try {
            link.requireArchivedBeforeDelete();
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }

        eventPublisher.deleted(link, clock.instant());
        linkTagRepository.deleteAllByLinkId(linkId);
        if (!shortLinkRepository.deleteByTenantIdAndId(tenantId, linkId, link.version())) {
            throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE);
        }
        AfterCommit.run(() -> redirectCacheSync.evict(link.tenantId(), link.domainId(), link.code().value()));
    }
}
