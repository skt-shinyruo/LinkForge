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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台应用与域名的写侧用例服务。
 *
 * <p>该服务在接受写请求时同时校验操作者身份与租户归属，避免调用方传入的
 * {@code tenantId} 与认证主体脱节。创建应用时，应用、默认策略和默认额度必须在同一事务中落库；
 * 任一步失败都会回滚整次开通。应用键和域名的并发唯一性最终由数据库约束裁决，约束冲突会转换为
 * 面向调用方的参数错误。</p>
 *
 * <p>本服务只负责平台资源本身的业务不变量，不代替接口层的角色授权。</p>
 */
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

    /**
     * 为租户创建一个启用状态的应用，并初始化默认策略与默认额度。
     *
     * <p>{@code applicationKey} 和 {@code displayName} 会先去除首尾空白；键长度上限为
     * {@code PlatformDefaults.APPLICATION_KEY_MAX_LENGTH}。同一租户内的应用键必须唯一，
     * 并发创建相同键时仅数据库最终接受的一次能够成功。</p>
     *
     * @param tenantId 资源所属租户
     * @param actor     发起操作的租户用户，必须有效且属于 {@code tenantId}
     * @param request   创建参数
     * @return 已创建应用的稳定标识与规范化后的展示数据
     * @throws BusinessException 参数、操作者或唯一性校验失败时抛出
     */
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
        try {
            applicationRepository.insert(application);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "applicationKey 已存在");
        }
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

    /**
     * 创建租户共享域名。
     *
     * <p>主机名通过 {@link Hostname} 解析并规范化后持久化；新域名默认为启用、第一方可信，
     * 但创建本身不会自动授权任何应用使用它。域名唯一性由数据库在并发写入时保证。</p>
     *
     * @param tenantId 域名所属租户
     * @param actor     发起操作的租户用户
     * @param hostname  待解析的主机名，不应包含路径或端口
     * @return 创建后的共享域名
     * @throws BusinessException 主机名非法、操作者越权或域名冲突时抛出
     */
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
        try {
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
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "域名已存在");
        }
        return new DomainResult(domainId, tenantId, null, normalizedHostname, DomainScope.TENANT_SHARED);
    }

    /**
     * 为指定应用创建专属域名。
     *
     * <p>应用必须存在、属于当前租户且处于启用状态。专属域名直接绑定该应用，后续使用时不依赖
     * 共享域名授权表；主机名仍受全局持久化唯一约束保护。</p>
     *
     * @param tenantId     应用与域名所属租户
     * @param actor        发起操作的租户用户
     * @param applicationId 应用标识
     * @param hostname     待解析的主机名
     * @return 创建后的应用专属域名
     * @throws BusinessException 应用不可用、主机名非法、操作者越权或域名冲突时抛出
     */
    @Transactional
    public DomainResult createApplicationDedicatedDomain(long tenantId, UserActor actor, long applicationId, String hostname) {
        requireActor(tenantId, actor);
        Application application = requireActiveApplication(tenantId, applicationId);
        String normalizedHostname;
        try {
            normalizedHostname = Hostname.parse(hostname).value();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ex.getMessage());
        }
        long domainId = idGenerator.nextId();
        try {
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
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "域名已存在");
        }
        return new DomainResult(domainId, tenantId, application.id(), normalizedHostname, DomainScope.APPLICATION_DEDICATED);
    }

    /**
     * 授权一个应用使用同租户的共享域名。
     *
     * <p>应用和域名均必须启用，且只有 {@link DomainScope#TENANT_SHARED} 域名可以建立授权关系。
     * 当前写入采用普通插入而非 upsert；同一授权的重复或并发提交可能触发存储唯一约束，
     * 因而调用方不应把该操作视为无条件幂等。</p>
     *
     * @param tenantId     应用与域名所属租户
     * @param actor        发起操作的租户用户
     * @param applicationId 获得使用权的应用标识
     * @param domainId     被授权的共享域名标识
     * @throws BusinessException 操作者越权、资源不存在或资源状态/范围不允许授权时抛出
     */
    @Transactional
    public void authorizeDomain(long tenantId, UserActor actor, long applicationId, long domainId) {
        requireActor(tenantId, actor);
        requireActiveApplication(tenantId, applicationId);
        Domain domain = domainRepository.findByTenantIdAndId(tenantId, domainId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "域名不存在"));
        if (domain.status() != DomainStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "域名未启用");
        }
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
        if (request.applicationKey().trim().length() > PlatformDefaults.APPLICATION_KEY_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "applicationKey 过长");
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

    private Application requireActiveApplication(long tenantId, long applicationId) {
        Application application = applicationRepository.findByTenantIdAndId(tenantId, applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用不存在"));
        if (!PlatformDefaults.APPLICATION_STATUS_ACTIVE.equals(application.status())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "应用未启用");
        }
        return application;
    }

}
