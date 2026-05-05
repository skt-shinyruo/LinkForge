package com.linkforge.platform.application;

import com.linkforge.foundation.config.CoreProperties;
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
import com.linkforge.platform.domain.PlatformDefaults;
import com.linkforge.platform.domain.TargetTrustClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

@Service
class LegacyApplicationBindingService {

    private static final String LEGACY_DEFAULT_APPLICATION_KEY = "legacy-default";
    private static final String LEGACY_DEFAULT_APPLICATION_NAME = "Legacy Default";
    private final SnowflakeIdGenerator idGenerator;
    private final ApplicationRepository applicationRepository;
    private final DomainRepository domainRepository;
    private final ApplicationQuotaRepository applicationQuotaRepository;
    private final ApplicationPolicyRepository applicationPolicyRepository;
    private final CoreProperties coreProperties;

    LegacyApplicationBindingService(
            SnowflakeIdGenerator idGenerator,
            ApplicationRepository applicationRepository,
            DomainRepository domainRepository,
            ApplicationQuotaRepository applicationQuotaRepository,
            ApplicationPolicyRepository applicationPolicyRepository,
            CoreProperties coreProperties
    ) {
        this.idGenerator = idGenerator;
        this.applicationRepository = applicationRepository;
        this.domainRepository = domainRepository;
        this.applicationQuotaRepository = applicationQuotaRepository;
        this.applicationPolicyRepository = applicationPolicyRepository;
        this.coreProperties = coreProperties;
    }

    @Transactional
    LegacyBinding ensureLegacyDefaultBinding(long tenantId) {
        Application application = applicationRepository.findByTenantIdAndApplicationKey(tenantId, LEGACY_DEFAULT_APPLICATION_KEY)
                .orElseGet(() -> createLegacyApplication(tenantId));
        String hostname = legacyHostname(tenantId);
        Domain domain = domainRepository.findByTenantIdAndHostname(tenantId, hostname)
                .orElseGet(() -> createLegacyDomain(tenantId, application.id(), hostname));
        return new LegacyBinding(application.id(), domain.id());
    }

    private Application createLegacyApplication(long tenantId) {
        long applicationId = idGenerator.nextId();
        Application application = new Application(
                applicationId,
                tenantId,
                LEGACY_DEFAULT_APPLICATION_KEY,
                LEGACY_DEFAULT_APPLICATION_NAME,
                PlatformDefaults.APPLICATION_STATUS_ACTIVE,
                null,
                null
        );
        applicationRepository.insert(application);
        applicationPolicyRepository.insert(new ApplicationPolicy(
                applicationId,
                DomainScope.APPLICATION_DEDICATED,
                PlatformDefaults.REDIRECT_STATUS_CODE,
                PlatformDefaults.PREVIEW_ENABLED,
                null,
                null
        ));
        applicationQuotaRepository.insert(new ApplicationQuota(
                applicationId,
                PlatformDefaults.MONTHLY_LINK_LIMIT,
                PlatformDefaults.MONTHLY_CLICK_LIMIT,
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

    private String legacyHostname(long tenantId) {
        String baseUrl = coreProperties == null ? null : coreProperties.getBaseUrl();
        String host = "legacy-host";
        if (baseUrl != null && !baseUrl.isBlank()) {
            try {
                URI uri = URI.create(baseUrl);
                if (uri.getHost() != null && !uri.getHost().isBlank()) {
                    host = uri.getHost().toLowerCase();
                }
            } catch (Exception ignored) {
                // fall through to synthetic host
            }
        }
        return "legacy-" + tenantId + "." + host;
    }

    record LegacyBinding(long applicationId, long domainId) {
    }
}
