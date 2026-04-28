package com.linkforge.shortlink.domain.event;

import java.time.LocalDateTime;

public record ShortLinkArchived(
        long linkId,
        long tenantId,
        Long domainId,
        String code,
        LocalDateTime archivedAtUtc
) implements ShortLinkDomainEvent {
}
