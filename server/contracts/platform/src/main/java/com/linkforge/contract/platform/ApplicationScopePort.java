package com.linkforge.contract.platform;

import java.util.Optional;

public interface ApplicationScopePort {

    void requireApplicationExists(long tenantId, long applicationId);

    void requireApplicationAndDomainAuthorized(long tenantId, long applicationId, long domainId);

    Optional<ApplicationQuotaView> findApplicationQuota(long tenantId, long applicationId);
}
