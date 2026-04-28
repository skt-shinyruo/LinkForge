package com.linkforge.platform.domain;

public class DomainAuthorizationPolicy {

    public void requireApplicationCanUseDomain(
            long applicationId,
            Domain domain,
            boolean sharedDomainAuthorized
    ) {
        if (domain.scope() == DomainScope.APPLICATION_DEDICATED) {
            if (domain.applicationId() == null || domain.applicationId() != applicationId) {
                throw new DomainAuthorizationException(
                        DomainAuthorizationException.Reason.DEDICATED_DOMAIN_MISMATCH
                );
            }
            return;
        }

        if (!sharedDomainAuthorized) {
            throw new DomainAuthorizationException(
                    DomainAuthorizationException.Reason.SHARED_DOMAIN_NOT_AUTHORIZED
            );
        }
    }
}
