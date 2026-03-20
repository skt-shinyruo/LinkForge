package com.linkforge.contract.shortlink;

import java.util.Optional;

/**
 * Synchronous ownership lookup for short links when eventual-consistent projections are not acceptable.
 */
public interface ShortLinkOwnershipLookupPort {

    Optional<ShortLinkOwnership> findByTenantIdAndId(long tenantId, long linkId);

    record ShortLinkOwnership(Long applicationId, Long domainId) {
    }
}
