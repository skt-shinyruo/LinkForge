package com.linkforge.shortlink.domain;

import java.time.LocalDateTime;

public record ShortLinkRevision(
        long linkId,
        String originalUrl,
        Long requestedBy,
        LocalDateTime createdAtUtc
) {
}
