package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.RedirectCacheInvalidationOutboxPort;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.RedirectCacheInvalidations;
import com.linkforge.shortlink.application.support.ShortLinkDomainExceptions;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkDomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class DeleteShortLinkCommandHandler {

    private final ShortLinkRepository shortLinkRepository;
    private final LinkTagRepository linkTagRepository;
    private final ShortLinkDomainEventDispatcher domainEventDispatcher;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final RedirectCacheInvalidationOutboxPort redirectCacheInvalidationOutbox;
    private final PostCommitHookPort postCommitHookPort;
    private final Clock clock;

    public DeleteShortLinkCommandHandler(
            ShortLinkRepository shortLinkRepository,
            LinkTagRepository linkTagRepository,
            ShortLinkDomainEventDispatcher domainEventDispatcher,
            RedirectCacheSyncPort redirectCacheSync,
            RedirectCacheInvalidationOutboxPort redirectCacheInvalidationOutbox,
            PostCommitHookPort postCommitHookPort,
            Clock clock
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.linkTagRepository = linkTagRepository;
        this.domainEventDispatcher = domainEventDispatcher;
        this.redirectCacheSync = redirectCacheSync;
        this.redirectCacheInvalidationOutbox = redirectCacheInvalidationOutbox;
        this.postCommitHookPort = postCommitHookPort;
        this.clock = clock;
    }

    @Transactional
    public void handle(long tenantId, long linkId) {
        ShortLink link = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND));

        Instant occurredAtUtc = clock.instant();
        LocalDateTime nowUtc = LocalDateTime.ofInstant(occurredAtUtc, ZoneOffset.UTC);
        try {
            link.markDeleted(nowUtc);
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }

        linkTagRepository.deleteAllByLinkId(linkId);
        if (!shortLinkRepository.deleteByTenantIdAndId(tenantId, linkId, link.version())) {
            throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE);
        }
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
}
