package com.linkforge.platform.application;

import com.linkforge.contract.platform.ApplicationQuotaView;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.contract.platform.DomainHostnameLookupPort;
import com.linkforge.contract.platform.LegacyApplicationBindingView;
import com.linkforge.contract.platform.LegacyApplicationProvisioningPort;
import com.linkforge.platform.application.port.DomainRepository;
import com.linkforge.platform.domain.Domain;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PlatformApplicationScopeAdapter implements ApplicationScopePort, DomainHostnameLookupPort, LegacyApplicationProvisioningPort {

    private final PlatformControlPlaneService platformControlPlaneService;
    private final DomainRepository domainRepository;
    private final LegacyApplicationBindingService legacyApplicationBindingService;

    public PlatformApplicationScopeAdapter(
            PlatformControlPlaneService platformControlPlaneService,
            DomainRepository domainRepository,
            LegacyApplicationBindingService legacyApplicationBindingService
    ) {
        this.platformControlPlaneService = platformControlPlaneService;
        this.domainRepository = domainRepository;
        this.legacyApplicationBindingService = legacyApplicationBindingService;
    }

    @Override
    public void requireApplicationAndDomainAuthorized(long tenantId, long applicationId, long domainId) {
        platformControlPlaneService.requireApplicationAndDomainAuthorized(tenantId, applicationId, domainId);
    }

    @Override
    public void requireApplicationExists(long tenantId, long applicationId) {
        platformControlPlaneService.requireApplicationExists(tenantId, applicationId);
    }

    @Override
    public Optional<ApplicationQuotaView> findApplicationQuota(long tenantId, long applicationId) {
        return platformControlPlaneService.findApplicationQuota(tenantId, applicationId)
                .map(quota -> new ApplicationQuotaView(
                        quota.applicationId(),
                        quota.monthlyLinkLimit(),
                        quota.monthlyClickLimit()
                ));
    }

    @Override
    public Optional<String> findDomainHostname(long tenantId, long domainId) {
        return domainRepository.findByTenantIdAndId(tenantId, domainId)
                .map(Domain::hostname);
    }

    @Override
    public Optional<Long> findDomainIdByHostname(long tenantId, String hostname) {
        return domainRepository.findByTenantIdAndHostname(tenantId, hostname)
                .map(Domain::id);
    }

    @Override
    public LegacyApplicationBindingView ensureLegacyDefaultBinding(long tenantId) {
        LegacyApplicationBindingService.LegacyBinding binding = legacyApplicationBindingService.ensureLegacyDefaultBinding(tenantId);
        return new LegacyApplicationBindingView(binding.applicationId(), binding.domainId());
    }
}
