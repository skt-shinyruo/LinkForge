package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.shortlink.application.LinkDto;
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.RedirectCacheInvalidationOutboxPort;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.RedirectCacheInvalidations;
import com.linkforge.shortlink.domain.ShortLink;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class RestoreShortLinkCommandHandler {

    private final ShortLinkRepository shortLinkRepository;
    private final ShortLinkDomainEventDispatcher domainEventDispatcher;
    private final LinkTagRepository linkTagRepository;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final RedirectCacheInvalidationOutboxPort redirectCacheInvalidationOutbox;
    private final ShortLinkDtoMapper dtoMapper;
    private final PostCommitHookPort postCommitHookPort;
    private final Clock clock;

    public RestoreShortLinkCommandHandler(
            ShortLinkRepository shortLinkRepository,
            ShortLinkDomainEventDispatcher domainEventDispatcher,
            LinkTagRepository linkTagRepository,
            RedirectCacheSyncPort redirectCacheSync,
            RedirectCacheInvalidationOutboxPort redirectCacheInvalidationOutbox,
            ShortLinkDtoMapper dtoMapper,
            PostCommitHookPort postCommitHookPort,
            Clock clock
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.domainEventDispatcher = domainEventDispatcher;
        this.linkTagRepository = linkTagRepository;
        this.redirectCacheSync = redirectCacheSync;
        this.redirectCacheInvalidationOutbox = redirectCacheInvalidationOutbox;
        this.dtoMapper = dtoMapper;
        this.postCommitHookPort = postCommitHookPort;
        this.clock = clock;
    }

    @Transactional
    public LinkDto handle(long tenantId, long linkId) {
        ShortLink link = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND));

        boolean restored = link.restore();
        if (restored) {
            Instant occurredAtUtc = clock.instant();
            if (!shortLinkRepository.update(link)) {
                throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE);
            }
            link.incrementVersion();
            domainEventDispatcher.publish(link, occurredAtUtc);
            RedirectCacheInvalidations.enqueueAndRunAfterCommit(
                    redirectCacheInvalidationOutbox,
                    postCommitHookPort,
                    redirectCacheSync,
                    link.tenantId(),
                    link.domainId(),
                    link.code().value()
            );
        }

        List<String> tags = linkTagRepository.findTagNamesByLinkId(linkId);
        return dtoMapper.toDto(link, tags);
    }
}
