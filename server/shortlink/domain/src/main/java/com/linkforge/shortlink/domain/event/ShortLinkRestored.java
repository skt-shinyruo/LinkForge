package com.linkforge.shortlink.domain.event;

public record ShortLinkRestored(
        long linkId,
        long tenantId,
        Long domainId,
        String code
) implements ShortLinkDomainEvent {
}
