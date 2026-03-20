package com.linkforge.platform.domain;

import java.time.LocalDateTime;

public record Application(
        long id,
        long tenantId,
        String applicationKey,
        String displayName,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
