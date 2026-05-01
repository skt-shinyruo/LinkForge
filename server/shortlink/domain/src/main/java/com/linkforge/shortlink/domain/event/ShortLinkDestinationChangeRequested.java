package com.linkforge.shortlink.domain.event;

public record ShortLinkDestinationChangeRequested(
        long linkId,
        long tenantId,
        Long domainId,
        String code,
        String beforeUrl,
        String afterUrl
) implements ShortLinkDomainEvent {
}
