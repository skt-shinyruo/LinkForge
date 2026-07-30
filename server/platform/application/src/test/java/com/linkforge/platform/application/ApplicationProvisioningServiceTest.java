package com.linkforge.platform.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.platform.application.port.ApplicationPolicyRepository;
import com.linkforge.platform.application.port.ApplicationQuotaRepository;
import com.linkforge.platform.application.port.ApplicationRepository;
import com.linkforge.platform.application.port.DomainRepository;
import com.linkforge.platform.domain.Application;
import com.linkforge.platform.domain.Domain;
import com.linkforge.platform.domain.DomainScope;
import com.linkforge.platform.domain.DomainStatus;
import com.linkforge.platform.domain.TargetTrustClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class ApplicationProvisioningServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "example.com:443",
            "example.com/path",
            "exa mple.com",
            "bad_host.example.com",
            "*.example.com",
            "localhost",
            "127.0.0.1",
            "::1",
            "example.com."
    })
    void createTenantSharedDomain_shouldRejectInvalidHostnames(String hostname) {
        ApplicationProvisioningService service = newService(mock(DomainRepository.class));

        assertThatThrownBy(() -> service.createTenantSharedDomain(1L, actor(), hostname))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("hostname 不合法");
    }

    @Test
    void createTenantSharedDomain_shouldNormalizeHostnameBeforeInsert() {
        DomainRepository domainRepository = mock(DomainRepository.class);
        ApplicationProvisioningService service = newService(domainRepository);

        DomainResult dto = service.createTenantSharedDomain(1L, actor(), " Go.Example.COM ");

        assertThat(dto.hostname()).isEqualTo("go.example.com");
        ArgumentCaptor<Domain> domainCaptor = ArgumentCaptor.forClass(Domain.class);
        verify(domainRepository).insert(domainCaptor.capture());
        assertThat(domainCaptor.getValue().hostname()).isEqualTo("go.example.com");
    }

    @Test
    void createApplication_shouldTranslateDuplicateApplicationKey() {
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        ApplicationProvisioningService service = newService(applicationRepository, mock(DomainRepository.class));
        doThrow(new DataIntegrityViolationException("uk_applications_tenant_key"))
                .when(applicationRepository)
                .insert(org.mockito.ArgumentMatchers.any(Application.class));

        assertThatThrownBy(() -> service.createApplication(
                1L,
                actor(),
                new CreateApplicationCommand("api", "API")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("applicationKey 已存在");
    }

    @Test
    void createApplication_shouldAcceptApplicationKeyAtDatabaseLimit() {
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        ApplicationProvisioningService service = newService(applicationRepository, mock(DomainRepository.class));
        String applicationKey = "a".repeat(64);

        ApplicationResult result = service.createApplication(
                1L,
                actor(),
                new CreateApplicationCommand(applicationKey, "API")
        );

        assertThat(result.applicationKey()).isEqualTo(applicationKey);
        verify(applicationRepository).insert(org.mockito.ArgumentMatchers.any(Application.class));
    }

    @Test
    void createApplication_shouldRejectApplicationKeyBeyondDatabaseLimitBeforeInsert() {
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        ApplicationProvisioningService service = newService(applicationRepository, mock(DomainRepository.class));

        assertThatThrownBy(() -> service.createApplication(
                1L,
                actor(),
                new CreateApplicationCommand("a".repeat(65), "API")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("applicationKey 过长");
        verify(applicationRepository, never()).insert(org.mockito.ArgumentMatchers.any(Application.class));
    }

    @Test
    void createTenantSharedDomain_shouldTranslateDuplicateHostname() {
        DomainRepository domainRepository = mock(DomainRepository.class);
        ApplicationProvisioningService service = newService(domainRepository);
        doThrow(new DataIntegrityViolationException("uk_domains_hostname"))
                .when(domainRepository)
                .insert(org.mockito.ArgumentMatchers.any(Domain.class));

        assertThatThrownBy(() -> service.createTenantSharedDomain(1L, actor(), "go.example.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("域名已存在");
    }

    @Test
    void authorizeDomain_shouldRejectInactiveTenantSharedDomain() {
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        DomainRepository domainRepository = mock(DomainRepository.class);
        ApplicationProvisioningService service = newService(applicationRepository, domainRepository);
        when(applicationRepository.findByTenantIdAndId(1L, 2001L))
                .thenReturn(Optional.of(new Application(2001L, 1L, "api", "API", "ACTIVE", null, null)));
        when(domainRepository.findByTenantIdAndId(1L, 3001L))
                .thenReturn(Optional.of(new Domain(
                        3001L,
                        1L,
                        null,
                        "go.example.com",
                        DomainScope.TENANT_SHARED,
                        DomainStatus.DISABLED,
                        TargetTrustClass.FIRST_PARTY,
                        null,
                        null
                )));

        assertThatThrownBy(() -> service.authorizeDomain(1L, actor(), 2001L, 3001L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("域名未启用");
        verify(domainRepository, never()).authorizeApplicationUse(2001L, 3001L);
    }

    @Test
    void authorizeDomain_shouldRejectDisabledApplication() {
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        DomainRepository domainRepository = mock(DomainRepository.class);
        ApplicationProvisioningService service = newService(applicationRepository, domainRepository);
        when(applicationRepository.findByTenantIdAndId(1L, 2001L))
                .thenReturn(Optional.of(new Application(2001L, 1L, "api", "API", "DISABLED", null, null)));

        assertThatThrownBy(() -> service.authorizeDomain(1L, actor(), 2001L, 3001L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用未启用");
        verify(domainRepository, never()).authorizeApplicationUse(2001L, 3001L);
    }

    private static ApplicationProvisioningService newService(DomainRepository domainRepository) {
        return newService(mock(ApplicationRepository.class), domainRepository);
    }

    private static ApplicationProvisioningService newService(
            ApplicationRepository applicationRepository,
            DomainRepository domainRepository
    ) {
        return new ApplicationProvisioningService(
                new SnowflakeIdGenerator(),
                applicationRepository,
                domainRepository,
                mock(ApplicationQuotaRepository.class),
                mock(ApplicationPolicyRepository.class)
        );
    }

    private static UserActor actor() {
        return new UserActor(1L, 9L, "admin@example.com", Set.of("TENANT_ADMIN"));
    }
}
