package com.linkforge.shortlink.domain.event;

import java.time.LocalDateTime;

public record ShortLinkUpdated(
        long linkId,
        long tenantId,
        Long domainId,
        String code,
        LocalDateTime updatedAtUtc
) implements ShortLinkDomainEvent {
}
