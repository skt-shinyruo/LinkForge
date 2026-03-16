package com.linkforge.contract.shortlink.event;

import com.linkforge.contract.shortlink.ShortLinkPublicSnapshot;

import java.time.Instant;

public record ShortLinkArchivedV1(
        String eventId,
        Instant occurredAtUtc,
        long tenantId,
        long linkId,
        String code,
        ShortLinkPublicSnapshot snapshot
) {
}

