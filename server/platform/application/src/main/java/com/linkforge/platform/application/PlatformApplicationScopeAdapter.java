package com.linkforge.platform.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.platform.ApplicationQuotaView;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.contract.platform.DomainHostnameLookupPort;
import com.linkforge.contract.platform.LegacyApplicationBindingView;
import com.linkforge.contract.platform.LegacyApplicationProvisioningPort;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.platform.application.port.ApplicationPolicyRepository;
import com.linkforge.platform.application.port.ApplicationQuotaRepository;
import com.linkforge.platform.application.port.ApplicationRepository;
import com.linkforge.platform.application.port.DomainRepository;
import com.linkforge.platform.domain.Application;
import com.linkforge.platform.domain.ApplicationPolicy;
import com.linkforge.platform.domain.ApplicationQuota;
import com.linkforge.platform.domain.Domain;
import com.linkforge.platform.domain.DomainScope;
import com.linkforge.platform.domain.DomainStatus;
import com.linkforge.platform.domain.TargetTrustClass;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class PlatformApplicationScopeAdapter implements ApplicationScopePort, DomainHostnameLookupPort, LegacyApplicationProvisioningPort {

    private static final String APPLICATION_STATUS_ACTIVE = "ACTIVE";
    private static final int DEFAULT_REDIRECT_STATUS_CODE = 302;

    private final ApplicationRepository applicationRepository;
    private final DomainRepository domainRepository;
    private final ApplicationQuotaRepository applicationQuotaRepository;
    private final ApplicationPolicyRepository applicationPolicyRepository;
    private final SnowflakeIdGenerator idGenerator;

    public PlatformApplicationScopeAdapter(
            ApplicationRepository applicationRepository,
            DomainRepository domainRepository,
            ApplicationQuotaRepository applicationQuotaRepository,
            ApplicationPolicyRepository applicationPolicyRepository,
            SnowflakeIdGenerator idGenerator
    ) {
        this.applicationRepository = applicationRepository;
        this.domainRepository = domainRepository;
        this.applicationQuotaRepository = applicationQuotaRepository;
        this.applicationPolicyRepository = applicationPolicyRepository;
        this.idGenerator = idGenerator;
    }

    @Override
    public void requireApplicationAndDomainAuthorized(long tenantId, long applicationId, long domainId) {
        requireApplicationExists(tenantId, applicationId);
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

    @Override
    public void requireApplicationExists(long tenantId, long applicationId) {
        applicationRepository.findByTenantIdAndId(tenantId, applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用不存在"));
    }

    @Override
    public Optional<ApplicationQuotaView> findApplicationQuota(long tenantId, long applicationId) {
        requireApplicationExists(tenantId, applicationId);
        return applicationQuotaRepository.findByApplicationId(applicationId)
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
    @Transactional
    public LegacyApplicationBindingView ensureLegacyDefaultBinding(
            long tenantId,
            String applicationKey,
            String applicationName,
            String hostname,
            long monthlyLinkLimit,
            long monthlyClickLimit
    ) {
        Application application = applicationRepository.findByTenantIdAndApplicationKey(tenantId, applicationKey)
                .orElseGet(() -> createLegacyApplication(tenantId, applicationKey, applicationName, monthlyLinkLimit, monthlyClickLimit));
        Domain domain = domainRepository.findByTenantIdAndHostname(tenantId, hostname)
                .orElseGet(() -> createLegacyDomain(tenantId, application.id(), hostname));
        return new LegacyApplicationBindingView(application.id(), domain.id());
    }

    private Application createLegacyApplication(
            long tenantId,
            String applicationKey,
            String applicationName,
            long monthlyLinkLimit,
            long monthlyClickLimit
    ) {
        long applicationId = idGenerator.nextId();
        Application application = new Application(
                applicationId,
                tenantId,
                applicationKey,
                applicationName,
                APPLICATION_STATUS_ACTIVE,
                null,
                null
        );
        applicationRepository.insert(application);
        applicationPolicyRepository.insert(new ApplicationPolicy(
                applicationId,
                DomainScope.APPLICATION_DEDICATED,
                DEFAULT_REDIRECT_STATUS_CODE,
                false,
                null,
                null
        ));
        applicationQuotaRepository.insert(new ApplicationQuota(
                applicationId,
                monthlyLinkLimit,
                monthlyClickLimit,
                null,
                null
        ));
        return application;
    }

    private Domain createLegacyDomain(long tenantId, long applicationId, String hostname) {
        long domainId = idGenerator.nextId();
        Domain domain = new Domain(
                domainId,
                tenantId,
                applicationId,
                hostname,
                DomainScope.APPLICATION_DEDICATED,
                DomainStatus.ACTIVE,
                TargetTrustClass.FIRST_PARTY,
                null,
                null
        );
        domainRepository.insert(domain);
        return domain;
    }
}
