package com.linkforge.shortlink.application.migration;

public record BackfillResult(long tenantId, long applicationId, long domainId, int updatedCount) {
}
