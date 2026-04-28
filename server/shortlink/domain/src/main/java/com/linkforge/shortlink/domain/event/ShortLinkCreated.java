package com.linkforge.shortlink.domain.event;

public record ShortLinkCreated(
        long linkId,
        long tenantId,
        Long domainId,
        String code
) implements ShortLinkDomainEvent {
}
