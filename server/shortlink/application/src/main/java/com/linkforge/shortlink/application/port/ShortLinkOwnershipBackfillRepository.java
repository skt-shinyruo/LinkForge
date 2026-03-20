package com.linkforge.shortlink.application.port;

public interface ShortLinkOwnershipBackfillRepository {

    int backfillTenant(long tenantId, long applicationId, long domainId);
}
