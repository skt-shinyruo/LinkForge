package com.linkforge.platform.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.platform.application.port.ApplicationRepository;
import com.linkforge.platform.application.port.ApplicationQuotaRepository;
import com.linkforge.platform.application.port.DomainRepository;
import com.linkforge.platform.domain.Domain;
import com.linkforge.platform.domain.DomainAuthorizationException;
import com.linkforge.platform.domain.DomainAuthorizationPolicy;
import com.linkforge.platform.domain.DomainScope;
import com.linkforge.platform.domain.ApplicationQuota;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PlatformControlPlaneService {

    private static final DomainAuthorizationPolicy DOMAIN_AUTHORIZATION_POLICY = new DomainAuthorizationPolicy();

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

    public ApplicationResult createApplication(
            long tenantId,
            UserActor actor,
            CreateApplicationCommand request
    ) {
        return provisioningService.createApplication(tenantId, actor, request);
    }

    public DomainResult createTenantSharedDomain(long tenantId, UserActor actor, String hostname) {
        return provisioningService.createTenantSharedDomain(tenantId, actor, hostname);
    }

    public DomainResult createApplicationDedicatedDomain(
            long tenantId,
            UserActor actor,
            long applicationId,
            String hostname
    ) {
        return provisioningService.createApplicationDedicatedDomain(tenantId, actor, applicationId, hostname);
    }

    public void authorizeTenantDomainForApplicationUse(long tenantId, UserActor actor, long applicationId, long domainId) {
        provisioningService.authorizeDomain(tenantId, actor, applicationId, domainId);
    }

    public void requireApplicationAndDomainAuthorized(long tenantId, long applicationId, long domainId) {
        applicationRepository.findByTenantIdAndId(tenantId, applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用不存在"));
        Domain domain = domainRepository.findByTenantIdAndId(tenantId, domainId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "域名不存在"));

        boolean sharedDomainAuthorized = domain.scope() == DomainScope.APPLICATION_DEDICATED
                || domainRepository.isApplicationAuthorizedForDomain(applicationId, domainId);
        try {
            DOMAIN_AUTHORIZATION_POLICY.requireApplicationCanUseDomain(applicationId, domain, sharedDomainAuthorized);
        } catch (DomainAuthorizationException e) {
            throw new BusinessException(ErrorCode.FORBIDDEN, domainAuthorizationMessage(e));
        }
    }

    private static String domainAuthorizationMessage(DomainAuthorizationException e) {
        if (e != null && e.reason() == DomainAuthorizationException.Reason.DEDICATED_DOMAIN_MISMATCH) {
            return "应用未绑定该专属域名";
        }
        return "应用未获授权使用该共享域名";
    }

    public void requireApplicationExists(long tenantId, long applicationId) {
        applicationRepository.findByTenantIdAndId(tenantId, applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用不存在"));
    }

    public List<ApplicationResult> listApplications(long tenantId) {
        return applicationRepository.listByTenantId(tenantId).stream()
                .map(application -> new ApplicationResult(
                        application.id(),
                        application.tenantId(),
                        application.applicationKey(),
                        application.displayName()
                ))
                .toList();
    }

    public List<DomainResult> listDomains(long tenantId) {
        return domainRepository.listByTenantId(tenantId).stream()
                .map(PlatformControlPlaneService::toDomainResult)
                .toList();
    }

    public List<DomainResult> listDomainsForApplication(long tenantId, long applicationId) {
        requireApplicationExists(tenantId, applicationId);
        return domainRepository.listUsableByApplication(tenantId, applicationId).stream()
                .map(PlatformControlPlaneService::toDomainResult)
                .toList();
    }

    public List<ApplicationResult> listAllApplications() {
        return applicationRepository.listAll().stream()
                .map(application -> new ApplicationResult(
                        application.id(),
                        application.tenantId(),
                        application.applicationKey(),
                        application.displayName()
                ))
                .toList();
    }

    public List<DomainResult> listAllDomains() {
        return domainRepository.listAll().stream()
                .map(PlatformControlPlaneService::toDomainResult)
                .toList();
    }

    public Optional<ApplicationQuota> findApplicationQuota(long tenantId, long applicationId) {
        requireApplicationExists(tenantId, applicationId);
        return applicationQuotaRepository.findByApplicationId(applicationId);
    }

    private static DomainResult toDomainResult(Domain domain) {
        return new DomainResult(
                domain.id(),
                domain.tenantId(),
                domain.applicationId(),
                domain.hostname(),
                domain.scope()
        );
    }
}
