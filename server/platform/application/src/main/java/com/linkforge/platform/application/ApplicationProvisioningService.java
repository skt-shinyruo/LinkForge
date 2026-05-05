package com.linkforge.platform.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.platform.application.port.ApplicationPolicyRepository;
import com.linkforge.platform.application.port.ApplicationQuotaRepository;
import com.linkforge.platform.application.port.ApplicationRepository;
import com.linkforge.platform.application.port.DomainRepository;
import com.linkforge.platform.domain.Application;
import com.linkforge.platform.domain.ApplicationPolicy;
import com.linkforge.platform.domain.ApplicationQuota;
import com.linkforge.platform.domain.Domain;
import com.linkforge.platform.domain.Hostname;
import com.linkforge.platform.domain.PlatformDefaults;
import com.linkforge.platform.domain.DomainScope;
import com.linkforge.platform.domain.DomainStatus;
import com.linkforge.platform.domain.TargetTrustClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationProvisioningService {

    private final SnowflakeIdGenerator idGenerator;
    private final ApplicationRepository applicationRepository;
    private final DomainRepository domainRepository;
    private final ApplicationQuotaRepository applicationQuotaRepository;
    private final ApplicationPolicyRepository applicationPolicyRepository;

    public ApplicationProvisioningService(
            SnowflakeIdGenerator idGenerator,
            ApplicationRepository applicationRepository,
            DomainRepository domainRepository,
            ApplicationQuotaRepository applicationQuotaRepository,
            ApplicationPolicyRepository applicationPolicyRepository
    ) {
        this.idGenerator = idGenerator;
        this.applicationRepository = applicationRepository;
        this.domainRepository = domainRepository;
        this.applicationQuotaRepository = applicationQuotaRepository;
        this.applicationPolicyRepository = applicationPolicyRepository;
    }

    @Transactional
    public ApplicationResult createApplication(long tenantId, UserActor actor, CreateApplicationCommand request) {
        requireActor(tenantId, actor);
        validateCreateRequest(request);

        long applicationId = idGenerator.nextId();
        Application application = new Application(
                applicationId,
                tenantId,
                request.applicationKey().trim(),
                request.displayName().trim(),
                PlatformDefaults.APPLICATION_STATUS_ACTIVE,
                null,
                null
        );
        applicationRepository.insert(application);
        applicationPolicyRepository.insert(new ApplicationPolicy(
                applicationId,
                PlatformDefaults.DEFAULT_DOMAIN_SCOPE,
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
        return new ApplicationResult(applicationId, tenantId, request.applicationKey().trim(), request.displayName().trim());
    }

    @Transactional
    public DomainResult createTenantSharedDomain(long tenantId, UserActor actor, String hostname) {
        requireActor(tenantId, actor);
        String normalizedHostname;
        try {
            normalizedHostname = Hostname.parse(hostname).value();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ex.getMessage());
        }
        long domainId = idGenerator.nextId();
        domainRepository.insert(new Domain(
                domainId,
                tenantId,
                null,
                normalizedHostname,
                DomainScope.TENANT_SHARED,
                DomainStatus.ACTIVE,
                TargetTrustClass.FIRST_PARTY,
                null,
                null
        ));
        return new DomainResult(domainId, tenantId, null, normalizedHostname, DomainScope.TENANT_SHARED);
    }

    @Transactional
    public DomainResult createApplicationDedicatedDomain(long tenantId, UserActor actor, long applicationId, String hostname) {
        requireActor(tenantId, actor);
        Application application = applicationRepository.findByTenantIdAndId(tenantId, applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用不存在"));
        String normalizedHostname;
        try {
            normalizedHostname = Hostname.parse(hostname).value();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ex.getMessage());
        }
        long domainId = idGenerator.nextId();
        domainRepository.insert(new Domain(
                domainId,
                tenantId,
                application.id(),
                normalizedHostname,
                DomainScope.APPLICATION_DEDICATED,
                DomainStatus.ACTIVE,
                TargetTrustClass.FIRST_PARTY,
                null,
                null
        ));
        return new DomainResult(domainId, tenantId, application.id(), normalizedHostname, DomainScope.APPLICATION_DEDICATED);
    }

    @Transactional
    public void authorizeDomain(long tenantId, UserActor actor, long applicationId, long domainId) {
        requireActor(tenantId, actor);
        applicationRepository.findByTenantIdAndId(tenantId, applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用不存在"));
        Domain domain = domainRepository.findByTenantIdAndId(tenantId, domainId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "域名不存在"));
        if (domain.scope() != DomainScope.TENANT_SHARED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅租户共享域名允许授权");
        }
        domainRepository.authorizeApplicationUse(applicationId, domainId);
    }

    private static void validateCreateRequest(CreateApplicationCommand request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求不能为空");
        }
        if (request.applicationKey() == null || request.applicationKey().trim().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "applicationKey 不能为空");
        }
        if (request.displayName() == null || request.displayName().trim().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "displayName 不能为空");
        }
    }

    private static UserActor requireActor(long tenantId, UserActor actor) {
        if (actor == null || actor.userId() <= 0 || actor.email() == null || actor.email().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "actor 无效");
        }
        if (actor.tenantId() != tenantId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "actor 租户不匹配");
        }
        return actor;
    }

}
