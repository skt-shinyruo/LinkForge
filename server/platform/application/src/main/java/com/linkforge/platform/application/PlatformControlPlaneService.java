package com.linkforge.platform.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.platform.application.port.ApplicationRepository;
import com.linkforge.platform.application.port.ApplicationQuotaRepository;
import com.linkforge.platform.application.port.DomainRepository;
import com.linkforge.platform.domain.Application;
import com.linkforge.platform.domain.Domain;
import com.linkforge.platform.domain.DomainAuthorizationException;
import com.linkforge.platform.domain.DomainAuthorizationPolicy;
import com.linkforge.platform.domain.DomainScope;
import com.linkforge.platform.domain.ApplicationQuota;
import com.linkforge.platform.domain.PlatformDefaults;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 平台控制面的应用层门面。
 *
 * <p>创建与授权命令委托给带事务的 {@link ApplicationProvisioningService}；面向具体应用的使用权查询
 * 会执行租户范围和应用启用状态校验，列表查询则保留停用记录供管理面展示。域名可用性由
 * {@link DomainAuthorizationPolicy} 作为唯一业务判定来源：专属域名必须绑定当前应用，共享域名
 * 必须存在显式授权。</p>
 *
 * <p>{@code listAll*} 方法不施加租户过滤，仅供已经完成平台级授权的调用方使用；本服务不会自行判断
 * 调用方是否为平台管理员。</p>
 */
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

    /**
     * 创建应用及其默认策略、额度，事务语义由 provisioning 服务提供。
     */
    public ApplicationResult createApplication(
            long tenantId,
            UserActor actor,
            CreateApplicationCommand request
    ) {
        return provisioningService.createApplication(tenantId, actor, request);
    }

    /**
     * 创建尚未授权给具体应用的租户共享域名。
     */
    public DomainResult createTenantSharedDomain(long tenantId, UserActor actor, String hostname) {
        return provisioningService.createTenantSharedDomain(tenantId, actor, hostname);
    }

    /**
     * 创建直接绑定到指定启用应用的专属域名。
     */
    public DomainResult createApplicationDedicatedDomain(
            long tenantId,
            UserActor actor,
            long applicationId,
            String hostname
    ) {
        return provisioningService.createApplicationDedicatedDomain(tenantId, actor, applicationId, hostname);
    }

    /**
     * 建立应用对租户共享域名的使用授权。
     */
    public void authorizeTenantDomainForApplicationUse(long tenantId, UserActor actor, long applicationId, long domainId) {
        provisioningService.authorizeDomain(tenantId, actor, applicationId, domainId);
    }

    /**
     * 校验应用能否使用指定域名。
     *
     * <p>查询始终带租户条件；应用不存在返回 {@code NOT_FOUND}，应用停用或域名策略不满足返回
     * {@code FORBIDDEN}。该方法只校验当前持久化快照，不锁定资源，调用方后续写入仍应在自己的事务中
     * 处理并发状态变化。</p>
     *
     * @throws BusinessException 应用/域名不存在、停用或授权关系不满足时抛出
     */
    public void requireApplicationAndDomainAuthorized(long tenantId, long applicationId, long domainId) {
        requireActiveApplication(tenantId, applicationId);
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
        if (e != null && e.reason() == DomainAuthorizationException.Reason.DOMAIN_NOT_ACTIVE) {
            return "域名未启用";
        }
        if (e != null && e.reason() == DomainAuthorizationException.Reason.DEDICATED_DOMAIN_MISMATCH) {
            return "应用未绑定该专属域名";
        }
        return "应用未获授权使用该共享域名";
    }

    /**
     * 要求应用存在于指定租户并处于启用状态。
     *
     * @throws BusinessException 应用不存在或未启用时抛出
     */
    public void requireApplicationExists(long tenantId, long applicationId) {
        requireActiveApplication(tenantId, applicationId);
    }

    /**
     * 列出租户下全部应用，包括非启用状态的应用。
     */
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

    /**
     * 列出租户下全部域名；不按启用状态或授权关系过滤。
     */
    public List<DomainResult> listDomains(long tenantId) {
        return domainRepository.listByTenantId(tenantId).stream()
                .map(PlatformControlPlaneService::toDomainResult)
                .toList();
    }

    /**
     * 列出指定启用应用当前可用的域名。
     *
     * <p>结果仅包含启用的已绑定专属域名和已显式授权的共享域名。</p>
     */
    public List<DomainResult> listDomainsForApplication(long tenantId, long applicationId) {
        requireApplicationExists(tenantId, applicationId);
        return domainRepository.listUsableByApplication(tenantId, applicationId).stream()
                .map(PlatformControlPlaneService::toDomainResult)
                .toList();
    }

    /**
     * 跨租户列出全部应用，仅供平台管理接口在外层完成授权后调用。
     */
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

    /**
     * 跨租户列出全部域名，仅供平台管理接口在外层完成授权后调用。
     */
    public List<DomainResult> listAllDomains() {
        return domainRepository.listAll().stream()
                .map(PlatformControlPlaneService::toDomainResult)
                .toList();
    }

    /**
     * 查询启用应用的额度配置。
     *
     * <p>应用存在但额度行缺失时返回 {@link Optional#empty()}，以暴露不完整的历史数据，而不是静默套用默认值。</p>
     *
     * @throws BusinessException 应用不存在或未启用时抛出
     */
    public Optional<ApplicationQuota> findApplicationQuota(long tenantId, long applicationId) {
        requireApplicationExists(tenantId, applicationId);
        return applicationQuotaRepository.findByApplicationId(applicationId);
    }

    private Application requireActiveApplication(long tenantId, long applicationId) {
        Application application = applicationRepository.findByTenantIdAndId(tenantId, applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用不存在"));
        if (!PlatformDefaults.APPLICATION_STATUS_ACTIVE.equals(application.status())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "应用未启用");
        }
        return application;
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
