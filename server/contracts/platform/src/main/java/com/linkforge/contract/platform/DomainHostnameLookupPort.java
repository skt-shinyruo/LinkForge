package com.linkforge.contract.platform;

import java.util.Optional;

public interface DomainHostnameLookupPort {

    Optional<String> findDomainHostname(long tenantId, long domainId);

    default Optional<Long> findDomainIdByHostname(long tenantId, String hostname) {
        return Optional.empty();
    }
}
