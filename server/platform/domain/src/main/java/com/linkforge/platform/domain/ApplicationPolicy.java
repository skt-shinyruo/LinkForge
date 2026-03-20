package com.linkforge.platform.domain;

import java.time.LocalDateTime;

public record ApplicationPolicy(
        long applicationId,
        DomainScope defaultDomainScope,
        int defaultRedirectStatusCode,
        boolean previewEnabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
