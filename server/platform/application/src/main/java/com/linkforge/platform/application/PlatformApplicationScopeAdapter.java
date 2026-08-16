package com.linkforge.platform.application;

import com.linkforge.contract.platform.ApplicationQuotaView;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.contract.platform.DomainHostnameLookupPort;
import com.linkforge.platform.application.port.DomainRepository;
import com.linkforge.platform.domain.Domain;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Platform 上下文对外发布的应用范围和域名查询适配器。
 *
 * <p>该适配器不复制授权规则：应用/域名校验统一委托给 {@link PlatformControlPlaneService}，并保留其
 * {@code NOT_FOUND}/{@code FORBIDDEN} 失败语义。只读查询不使用缓存，返回值反映调用时的数据库快照。</p>
 */
@Component
public class PlatformApplicationScopeAdapter implements ApplicationScopePort, DomainHostnameLookupPort {

    private final PlatformControlPlaneService platformControlPlaneService;
    private final DomainRepository domainRepository;

    public PlatformApplicationScopeAdapter(
            PlatformControlPlaneService platformControlPlaneService,
            DomainRepository domainRepository
    ) {
        this.platformControlPlaneService = platformControlPlaneService;
        this.domainRepository = domainRepository;
    }

    /**
     * 按 Platform 的唯一授权策略校验应用和域名；失败异常直接传播给调用上下文。
     */
    @Override
    public void requireApplicationAndDomainAuthorized(long tenantId, long applicationId, long domainId) {
        platformControlPlaneService.requireApplicationAndDomainAuthorized(tenantId, applicationId, domainId);
    }

    /**
     * 校验应用属于租户且处于启用状态。
     */
    @Override
    public void requireApplicationExists(long tenantId, long applicationId) {
        platformControlPlaneService.requireApplicationExists(tenantId, applicationId);
    }

    /**
     * 返回应用显式配置的额度；应用存在但额度行缺失时保持 {@link Optional#empty()}。
     */
    @Override
    public Optional<ApplicationQuotaView> findApplicationQuota(long tenantId, long applicationId) {
        return platformControlPlaneService.findApplicationQuota(tenantId, applicationId)
                .map(quota -> new ApplicationQuotaView(
                        quota.applicationId(),
                        quota.monthlyLinkLimit(),
                        quota.monthlyClickLimit()
                ));
    }

    /**
     * 按租户和域名标识查询主机名，不跨租户回退。
     */
    @Override
    public Optional<String> findDomainHostname(long tenantId, long domainId) {
        return domainRepository.findByTenantIdAndId(tenantId, domainId)
                .map(Domain::hostname);
    }

    /**
     * 按租户和已规范化主机名查询域名标识；未命中时返回空值。
     */
    @Override
    public Optional<Long> findDomainIdByHostname(long tenantId, String hostname) {
        return domainRepository.findByTenantIdAndHostname(tenantId, hostname)
                .map(Domain::id);
    }

}
