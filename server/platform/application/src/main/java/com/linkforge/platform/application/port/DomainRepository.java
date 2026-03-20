package com.linkforge.platform.application.port;

import com.linkforge.platform.domain.Domain;

import java.util.List;
import java.util.Optional;

public interface DomainRepository {

    void insert(Domain domain);

    Optional<Domain> findByTenantIdAndId(long tenantId, long domainId);

    void authorizeApplicationUse(long applicationId, long domainId);

    boolean isApplicationAuthorizedForDomain(long applicationId, long domainId);

    Optional<Domain> findByTenantIdAndHostname(long tenantId, String hostname);

    List<Domain> listByTenantId(long tenantId);

    List<Domain> listUsableByApplication(long tenantId, long applicationId);

    List<Domain> listAll();
}
