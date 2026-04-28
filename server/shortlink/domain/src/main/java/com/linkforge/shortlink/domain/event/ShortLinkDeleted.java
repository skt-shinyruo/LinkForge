package com.linkforge.shortlink.domain.event;

import java.time.LocalDateTime;

public record ShortLinkDeleted(
        long linkId,
        long tenantId,
        Long domainId,
        String code,
        LocalDateTime deletedAtUtc
) implements ShortLinkDomainEvent {
}
