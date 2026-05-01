package com.linkforge.platform.domain;

import java.time.LocalDateTime;

public record ApplicationPolicy(
        long applicationId,
        DomainScope defaultDomainScope,
        int defaultRedirectStatusCode,
        boolean previewEnabled,
        TargetTrustClass targetTrustClass,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public ApplicationPolicy {
        if (applicationId <= 0) {
            throw new IllegalArgumentException("applicationId must be positive");
        }
        defaultDomainScope = defaultDomainScope == null ? DomainScope.TENANT_SHARED : defaultDomainScope;
        targetTrustClass = targetTrustClass == null ? TargetTrustClass.FIRST_PARTY : targetTrustClass;
    }

    public ApplicationPolicy(
            long applicationId,
            DomainScope defaultDomainScope,
            int defaultRedirectStatusCode,
            boolean previewEnabled,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(
                applicationId,
                defaultDomainScope,
                defaultRedirectStatusCode,
                previewEnabled,
                TargetTrustClass.FIRST_PARTY,
                createdAt,
                updatedAt
        );
    }
}
