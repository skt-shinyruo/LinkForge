package com.linkforge.platform.domain;

import java.time.LocalDateTime;

public record Domain(
        long id,
        long tenantId,
        Long applicationId,
        String hostname,
        DomainScope scope,
        DomainStatus status,
        TargetTrustClass trustClass,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
