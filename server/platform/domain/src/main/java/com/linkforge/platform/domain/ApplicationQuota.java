package com.linkforge.platform.domain;

import java.time.LocalDateTime;

public record ApplicationQuota(
        long applicationId,
        long monthlyLinkLimit,
        long monthlyClickLimit,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public ApplicationQuota {
        if (applicationId <= 0) {
            throw new IllegalArgumentException("applicationId must be positive");
        }
        MonthlyLinkLimit.of(monthlyLinkLimit);
        MonthlyLinkLimit.of(monthlyClickLimit);
    }

    public static ApplicationQuota create(
            long applicationId,
            MonthlyLinkLimit monthlyLinkLimit,
            MonthlyLinkLimit monthlyClickLimit
    ) {
        return new ApplicationQuota(
                applicationId,
                monthlyLinkLimit == null ? MonthlyLinkLimit.unlimited().value() : monthlyLinkLimit.value(),
                monthlyClickLimit == null ? MonthlyLinkLimit.unlimited().value() : monthlyClickLimit.value(),
                null,
                null
        );
    }
}
