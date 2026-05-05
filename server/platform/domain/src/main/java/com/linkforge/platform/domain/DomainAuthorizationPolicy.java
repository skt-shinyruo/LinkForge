package com.linkforge.platform.domain;

public class DomainAuthorizationPolicy {

    public void requireApplicationCanUseDomain(
            long applicationId,
            Domain domain,
            boolean sharedDomainAuthorized
    ) {
        if (domain.status() != DomainStatus.ACTIVE) {
            throw new DomainAuthorizationException(DomainAuthorizationException.Reason.DOMAIN_NOT_ACTIVE);
        }

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
