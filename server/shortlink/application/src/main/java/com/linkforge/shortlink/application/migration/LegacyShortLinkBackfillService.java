package com.linkforge.shortlink.application.migration;

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
import com.linkforge.platform.domain.TargetTrustClass;
import com.linkforge.shortlink.application.port.ShortLinkOwnershipBackfillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

@Service
public class LegacyShortLinkBackfillService {

    public static final String LEGACY_DEFAULT_APPLICATION_KEY = "legacy-default";
    public static final String LEGACY_DEFAULT_APPLICATION_NAME = "Legacy Default";
    static final long DEFAULT_MONTHLY_LINK_LIMIT = 10_000L;
    static final long DEFAULT_MONTHLY_CLICK_LIMIT = 1_000_000L;

    private final SnowflakeIdGenerator idGenerator;
    private final ApplicationRepository applicationRepository;
    private final DomainRepository domainRepository;
    private final ApplicationQuotaRepository applicationQuotaRepository;
    private final ApplicationPolicyRepository applicationPolicyRepository;
    private final ShortLinkOwnershipBackfillRepository backfillRepository;
    private final CoreProperties coreProperties;

    public LegacyShortLinkBackfillService(
            SnowflakeIdGenerator idGenerator,
            ApplicationRepository applicationRepository,
            DomainRepository domainRepository,
            ApplicationQuotaRepository applicationQuotaRepository,
            ApplicationPolicyRepository applicationPolicyRepository,
            ShortLinkOwnershipBackfillRepository backfillRepository,
            CoreProperties coreProperties
    ) {
        this.idGenerator = idGenerator;
        this.applicationRepository = applicationRepository;
        this.domainRepository = domainRepository;
        this.applicationQuotaRepository = applicationQuotaRepository;
        this.applicationPolicyRepository = applicationPolicyRepository;
        this.backfillRepository = backfillRepository;
        this.coreProperties = coreProperties;
    }

    @Transactional
    public BackfillResult backfillTenant(long tenantId) {
        Application application = applicationRepository.findByTenantIdAndApplicationKey(tenantId, LEGACY_DEFAULT_APPLICATION_KEY)
                .orElseGet(() -> createLegacyApplication(tenantId));
        Domain domain = domainRepository.findByTenantIdAndHostname(tenantId, legacyHostname(tenantId))
                .orElseGet(() -> createLegacyDomain(tenantId, application.id()));

        int updated = backfillRepository.backfillTenant(tenantId, application.id(), domain.id());
        return new BackfillResult(tenantId, application.id(), domain.id(), updated);
    }

    private Application createLegacyApplication(long tenantId) {
        long applicationId = idGenerator.nextId();
        Application application = new Application(
                applicationId,
                tenantId,
                LEGACY_DEFAULT_APPLICATION_KEY,
                LEGACY_DEFAULT_APPLICATION_NAME,
                "ACTIVE",
                null,
                null
        );
        applicationRepository.insert(application);
        applicationPolicyRepository.insert(new ApplicationPolicy(
                applicationId,
                DomainScope.APPLICATION_DEDICATED,
                302,
                false,
                null,
                null
        ));
        applicationQuotaRepository.insert(new ApplicationQuota(
                applicationId,
                DEFAULT_MONTHLY_LINK_LIMIT,
                DEFAULT_MONTHLY_CLICK_LIMIT,
                null,
                null
        ));
        return application;
    }

    private Domain createLegacyDomain(long tenantId, long applicationId) {
        long domainId = idGenerator.nextId();
        Domain domain = new Domain(
                domainId,
                tenantId,
                applicationId,
                legacyHostname(tenantId),
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

    public record BackfillResult(long tenantId, long applicationId, long domainId, int updatedCount) {
    }
}
