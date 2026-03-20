package com.linkforge.platform.domain;

import java.time.LocalDateTime;

public record ApplicationQuota(
        long applicationId,
        long monthlyLinkLimit,
        long monthlyClickLimit,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
