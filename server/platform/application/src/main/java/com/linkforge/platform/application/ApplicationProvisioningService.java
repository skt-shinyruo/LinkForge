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
import com.linkforge.platform.domain.DomainScope;
import com.linkforge.platform.domain.DomainStatus;
import com.linkforge.platform.domain.TargetTrustClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.IDN;
import java.util.Locale;

@Service
public class ApplicationProvisioningService {

    static final String APPLICATION_STATUS_ACTIVE = "ACTIVE";
    static final long DEFAULT_MONTHLY_LINK_LIMIT = 10_000L;
    static final long DEFAULT_MONTHLY_CLICK_LIMIT = 1_000_000L;
    static final int DEFAULT_REDIRECT_STATUS_CODE = 302;

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
    public ApplicationDto createApplication(long tenantId, UserActor actor, CreateApplicationRequest request) {
        requireActor(tenantId, actor);
        validateCreateRequest(request);

        long applicationId = idGenerator.nextId();
        Application application = new Application(
                applicationId,
                tenantId,
                request.applicationKey().trim(),
                request.displayName().trim(),
                APPLICATION_STATUS_ACTIVE,
                null,
                null
        );
        applicationRepository.insert(application);
        applicationPolicyRepository.insert(new ApplicationPolicy(
                applicationId,
                DomainScope.TENANT_SHARED,
                DEFAULT_REDIRECT_STATUS_CODE,
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
        return new ApplicationDto(applicationId, tenantId, request.applicationKey().trim(), request.displayName().trim());
    }

    @Transactional
    public DomainDto createTenantSharedDomain(long tenantId, UserActor actor, String hostname) {
        requireActor(tenantId, actor);
        String normalizedHostname = normalizeHostname(hostname);
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
        return new DomainDto(domainId, tenantId, null, normalizedHostname, DomainScope.TENANT_SHARED);
    }

    @Transactional
    public DomainDto createApplicationDedicatedDomain(long tenantId, UserActor actor, long applicationId, String hostname) {
        requireActor(tenantId, actor);
        Application application = applicationRepository.findByTenantIdAndId(tenantId, applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应用不存在"));
        String normalizedHostname = normalizeHostname(hostname);
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
        return new DomainDto(domainId, tenantId, application.id(), normalizedHostname, DomainScope.APPLICATION_DEDICATED);
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

    private static void validateCreateRequest(CreateApplicationRequest request) {
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

    private static String normalizeHostname(String hostname) {
        if (hostname == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "hostname 不能为空");
        }
        String value = hostname.trim().toLowerCase();
        if (value.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "hostname 不能为空");
        }
        if (hasInvalidHostnameCharacters(value)) {
            throw invalidHostname();
        }
        String ascii;
        try {
            ascii = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            throw invalidHostname();
        }
        if (ascii.length() > 253
                || ascii.startsWith(".")
                || ascii.endsWith(".")
                || ascii.contains("..")
                || "localhost".equals(ascii)
                || ascii.endsWith(".localhost")
                || looksLikeIpv4Address(ascii)) {
            throw invalidHostname();
        }
        String[] labels = ascii.split("\\.");
        if (labels.length < 2) {
            throw invalidHostname();
        }
        for (String label : labels) {
            if (!isValidHostnameLabel(label)) {
                throw invalidHostname();
            }
        }
        return ascii;
    }

    private static boolean hasInvalidHostnameCharacters(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isWhitespace(ch) || ch == ':' || ch == '/' || ch == '\\' || ch == '@' || ch == '*') {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeIpv4Address(String value) {
        return value.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private static boolean isValidHostnameLabel(String label) {
        if (label.isBlank() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) {
            return false;
        }
        for (int i = 0; i < label.length(); i++) {
            char ch = label.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static BusinessException invalidHostname() {
        return new BusinessException(ErrorCode.BAD_REQUEST, "hostname 不合法");
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

    public record CreateApplicationRequest(String applicationKey, String displayName) {
    }

    public record ApplicationDto(long id, long tenantId, String applicationKey, String displayName) {
    }

    public record DomainDto(long id, long tenantId, Long applicationId, String hostname, DomainScope scope) {
    }
}
