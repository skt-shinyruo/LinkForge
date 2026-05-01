package com.linkforge.shortlink.domain.event;

import java.util.Set;

public record ShortLinkTagsChanged(
        long linkId,
        long tenantId,
        Long domainId,
        String code,
        Set<String> tags
) implements ShortLinkDomainEvent {

    public ShortLinkTagsChanged {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }
}
