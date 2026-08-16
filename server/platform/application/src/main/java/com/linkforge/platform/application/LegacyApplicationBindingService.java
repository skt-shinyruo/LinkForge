package com.linkforge.platform.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.platform.application.port.ApplicationPolicyRepository;
import com.linkforge.platform.application.port.ApplicationQuotaRepository;
import com.linkforge.platform.application.port.ApplicationRepository;
import com.linkforge.platform.application.port.DomainRepository;
import com.linkforge.platform.application.port.LegacyBindingLockRepository;
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

/**
 * 为尚未显式选择应用和域名的旧接口提供兼容绑定。
 *
 * <p>同一租户的调用先获取 Platform 自有锁行，再执行 get-or-reconcile。只有 ACTIVE 应用和完整、
 * ACTIVE 的专属域名绑定可以复用；策略与额度每次都收敛到当前 legacy 默认值。停用或归属冲突不会
 * 被自动放宽，而是返回确定的业务错误。</p>
 */
@Service
class LegacyApplicationBindingService {

    private static final String LEGACY_DEFAULT_APPLICATION_KEY = "legacy-default";
    private static final String LEGACY_DEFAULT_APPLICATION_NAME = "Legacy Default";
    private final SnowflakeIdGenerator idGenerator;
    private final ApplicationRepository applicationRepository;
    private final DomainRepository domainRepository;
    private final ApplicationQuotaRepository applicationQuotaRepository;
    private final ApplicationPolicyRepository applicationPolicyRepository;
    private final LegacyBindingLockRepository legacyBindingLockRepository;
    private final CoreProperties coreProperties;

    LegacyApplicationBindingService(
            SnowflakeIdGenerator idGenerator,
            ApplicationRepository applicationRepository,
            DomainRepository domainRepository,
            ApplicationQuotaRepository applicationQuotaRepository,
            ApplicationPolicyRepository applicationPolicyRepository,
            LegacyBindingLockRepository legacyBindingLockRepository,
            CoreProperties coreProperties
    ) {
        this.idGenerator = idGenerator;
        this.applicationRepository = applicationRepository;
        this.domainRepository = domainRepository;
        this.applicationQuotaRepository = applicationQuotaRepository;
        this.applicationPolicyRepository = applicationPolicyRepository;
        this.legacyBindingLockRepository = legacyBindingLockRepository;
        this.coreProperties = coreProperties;
    }

    /**
     * 返回租户的旧接口默认绑定，并在租户级事务锁内补齐可安全修复的部分状态。
     *
     * <p>默认域名为 {@code legacy-{tenantId}.{baseHost}}；{@code baseHost} 优先取
     * {@code core.base-url} 的主机名，配置缺失或非法时使用 {@code legacy-host}。</p>
     *
     * @param tenantId 租户标识
     * @return 已验证为 ACTIVE 且归属、绑定和默认配置完整的应用/专属域名标识
     */
    @Transactional
    LegacyBinding ensureLegacyDefaultBinding(long tenantId) {
        if (tenantId <= 0L) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "租户标识无效");
        }
        legacyBindingLockRepository.lockTenant(tenantId);
        Application application = applicationRepository.findByTenantIdAndApplicationKey(tenantId, LEGACY_DEFAULT_APPLICATION_KEY)
                .orElseGet(() -> createLegacyApplication(tenantId));
        requireReusableApplication(tenantId, application);
        String hostname = legacyHostname(tenantId);
        Domain domain = domainRepository.findByTenantIdAndHostname(tenantId, hostname)
                .orElseGet(() -> findConflictingDomainOrCreate(tenantId, application.id(), hostname));
        requireReusableDomain(tenantId, application.id(), hostname, domain);
        reconcileConfiguration(application.id());
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
        return application;
    }

    private void reconcileConfiguration(long applicationId) {
        applicationPolicyRepository.upsert(new ApplicationPolicy(
                applicationId,
                DomainScope.APPLICATION_DEDICATED,
                PlatformDefaults.REDIRECT_STATUS_CODE,
                PlatformDefaults.PREVIEW_ENABLED,
                null,
                null
        ));
        applicationQuotaRepository.upsert(new ApplicationQuota(
                applicationId,
                PlatformDefaults.MONTHLY_LINK_LIMIT,
                PlatformDefaults.MONTHLY_CLICK_LIMIT,
                null,
                null
        ));
    }

    private Domain findConflictingDomainOrCreate(long tenantId, long applicationId, String hostname) {
        Domain existing = domainRepository.findByHostname(hostname).orElse(null);
        if (existing != null) {
            if (existing.tenantId() != tenantId) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Legacy 默认域名已被其他租户占用");
            }
            return existing;
        }
        return createLegacyDomain(tenantId, applicationId, hostname);
    }

    private static void requireReusableApplication(long tenantId, Application application) {
        if (application.tenantId() != tenantId
                || !LEGACY_DEFAULT_APPLICATION_KEY.equals(application.applicationKey())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Legacy 默认应用归属错误");
        }
        if (!PlatformDefaults.APPLICATION_STATUS_ACTIVE.equals(application.status())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Legacy 默认应用未启用");
        }
    }

    private static void requireReusableDomain(
            long tenantId,
            long applicationId,
            String hostname,
            Domain domain
    ) {
        if (domain.tenantId() != tenantId) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Legacy 默认域名已被其他租户占用");
        }
        if (!hostname.equals(domain.hostname())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Legacy 默认域名主机名错误");
        }
        if (domain.status() != DomainStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Legacy 默认域名未启用");
        }
        if (domain.scope() != DomainScope.APPLICATION_DEDICATED
                || domain.applicationId() == null
                || domain.applicationId() != applicationId) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Legacy 默认域名绑定错误");
        }
        if (domain.trustClass() != TargetTrustClass.FIRST_PARTY) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Legacy 默认域名信任配置错误");
        }
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
                // 非法配置不阻断旧接口开通，继续使用可预测的合成主机名。
            }
        }
        return "legacy-" + tenantId + "." + host;
    }

    record LegacyBinding(long applicationId, long domainId) {
    }
}
