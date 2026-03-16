package com.linkforge.contract.shortlink.event;

import com.linkforge.contract.shortlink.ShortLinkPublicSnapshot;

import java.time.Instant;

public record ShortLinkRestoredV1(
        String eventId,
        Instant occurredAtUtc,
        long tenantId,
        long linkId,
        String code,
        ShortLinkPublicSnapshot snapshot
) {
}

