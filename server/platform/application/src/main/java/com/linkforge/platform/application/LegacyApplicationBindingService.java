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

/**
 * 为尚未显式选择应用和域名的旧接口提供兼容绑定。
 *
 * <p>首次访问某租户时，该服务在一个事务中按需创建固定键的默认应用、默认策略、默认额度和专属域名；
 * 已存在的应用或域名会被复用。它不会修复“应用已存在但策略或额度缺失”之类的部分历史数据，
 * 也不会重新校验既有域名的状态及其与默认应用的绑定关系。</p>
 *
 * <p>当前实现采用先查询后插入，没有分布式锁、upsert 或冲突重读。数据库唯一约束可以防止生成两份
 * 默认资源，但并发首次开通时失败的一方会收到持久化异常，需要由上层在新事务中重试。</p>
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

    /**
     * 返回租户的旧接口默认绑定，不存在时在当前事务内创建。
     *
     * <p>默认域名为 {@code legacy-{tenantId}.{baseHost}}；{@code baseHost} 优先取
     * {@code core.base-url} 的主机名，配置缺失或非法时使用 {@code legacy-host}。</p>
     *
     * @param tenantId 租户标识
     * @return 可供旧接口填充的应用和专属域名标识
     */
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
                // 非法配置不阻断旧接口开通，继续使用可预测的合成主机名。
            }
        }
        return "legacy-" + tenantId + "." + host;
    }

    record LegacyBinding(long applicationId, long domainId) {
    }
}
