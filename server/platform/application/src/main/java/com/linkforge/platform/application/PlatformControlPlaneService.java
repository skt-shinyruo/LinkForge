package com.linkforge.platform.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.platform.application.port.ApplicationRepository;
import com.linkforge.platform.application.port.ApplicationQuotaRepository;
import com.linkforge.platform.application.port.DomainRepository;
import com.linkforge.platform.domain.Domain;
import com.linkforge.platform.domain.DomainScope;
import com.linkforge.platform.domain.ApplicationQuota;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PlatformControlPlaneService {

    private final ApplicationProvisioningService provisioningService;
    private final ApplicationRepository applicationRepository;
    private final ApplicationQuotaRepository applicationQuotaRepository;
    private final DomainRepository domainRepository;

    public PlatformControlPlaneService(
            ApplicationProvisioningService provisioningService,
            ApplicationRepository applicationRepository,
            ApplicationQuotaRepository applicationQuotaRepository,
            DomainRepository domainRepository
    ) {
        this.provisioningService = provisioningService;
        this.applicationRepository = applicationRepository;
        this.applicationQuotaRepository = applicationQuotaRepository;
        this.domainRepository = domainRepository;
    }

    public ApplicationProvisioningService.ApplicationDto createApplication(
            long tenantId,
            ApplicationProvisioningService.CreateApplicationRequest request
    ) {
        return provisioningService.createApplication(tenantId, request);
    }

    public ApplicationProvisioningService.DomainDto createTenantSharedDomain(long tenantId, String hostname) {
        return provisioningService.createTenantSharedDomain(tenantId, hostname);
    }

    public ApplicationProvisioningService.DomainDto createApplicationDedicatedDomain(
            long tenantId,
            long applicationId,
            String hostname
    ) {
        return provisioningService.createApplicationDedicatedDomain(tenantId, applicationId, hostname);
    }

    public void authorizeTenantDomainForApplicationUse(long tenantId, long applicationId, long domainId) {
        provisioningService.authorizeDomain(tenantId, applicationId, domainId);
    }

    public void requireApplicationAndDomainAuthorized(long tenantId, long applicationId, long domainId) {
        applicationRepository.findByTenantIdAndId(tenantId, applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用不存在"));
        Domain domain = domainRepository.findByTenantIdAndId(tenantId, domainId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "域名不存在"));

        if (domain.scope() == DomainScope.APPLICATION_DEDICATED) {
            if (domain.applicationId() == null || domain.applicationId() != applicationId) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "应用未绑定该专属域名");
            }
            return;
        }

        if (!domainRepository.isApplicationAuthorizedForDomain(applicationId, domainId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "应用未获授权使用该共享域名");
        }
    }

    public void requireApplicationExists(long tenantId, long applicationId) {
        applicationRepository.findByTenantIdAndId(tenantId, applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用不存在"));
    }

    public List<ApplicationProvisioningService.ApplicationDto> listApplications(long tenantId) {
        return applicationRepository.listByTenantId(tenantId).stream()
                .map(application -> new ApplicationProvisioningService.ApplicationDto(
                        application.id(),
                        application.tenantId(),
                        application.applicationKey(),
                        application.displayName()
                ))
                .toList();
    }

    public List<ApplicationProvisioningService.DomainDto> listDomains(long tenantId) {
        return domainRepository.listByTenantId(tenantId).stream()
                .map(PlatformControlPlaneService::toDomainDto)
                .toList();
    }

    public List<ApplicationProvisioningService.DomainDto> listDomainsForApplication(long tenantId, long applicationId) {
        requireApplicationExists(tenantId, applicationId);
        return domainRepository.listUsableByApplication(tenantId, applicationId).stream()
                .map(PlatformControlPlaneService::toDomainDto)
                .toList();
    }

    public List<ApplicationProvisioningService.ApplicationDto> listAllApplications() {
        return applicationRepository.listAll().stream()
                .map(application -> new ApplicationProvisioningService.ApplicationDto(
                        application.id(),
                        application.tenantId(),
                        application.applicationKey(),
                        application.displayName()
                ))
                .toList();
    }

    public List<ApplicationProvisioningService.DomainDto> listAllDomains() {
        return domainRepository.listAll().stream()
                .map(PlatformControlPlaneService::toDomainDto)
                .toList();
    }

    public Optional<ApplicationQuota> findApplicationQuota(long tenantId, long applicationId) {
        requireApplicationExists(tenantId, applicationId);
        return applicationQuotaRepository.findByApplicationId(applicationId);
    }

    private static ApplicationProvisioningService.DomainDto toDomainDto(Domain domain) {
        return new ApplicationProvisioningService.DomainDto(
                domain.id(),
                domain.tenantId(),
                domain.applicationId(),
                domain.hostname(),
                domain.scope()
        );
    }
}
