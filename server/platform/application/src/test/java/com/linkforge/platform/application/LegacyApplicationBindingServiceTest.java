package com.linkforge.platform.application;

import com.linkforge.contract.api.BusinessException;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyApplicationBindingServiceTest {

    @Test
    void ensureLegacyDefaultBinding_shouldOwnLegacyDefaultsAndHostnamePolicy() {
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        DomainRepository domainRepository = mock(DomainRepository.class);
        ApplicationQuotaRepository quotaRepository = mock(ApplicationQuotaRepository.class);
        ApplicationPolicyRepository policyRepository = mock(ApplicationPolicyRepository.class);
        LegacyBindingLockRepository lockRepository = mock(LegacyBindingLockRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(101L, 202L);
        when(applicationRepository.findByTenantIdAndApplicationKey(7L, "legacy-default")).thenReturn(Optional.empty());
        when(domainRepository.findByTenantIdAndHostname(7L, "legacy-7.links.example")).thenReturn(Optional.empty());
        when(domainRepository.findByHostname("legacy-7.links.example")).thenReturn(Optional.empty());

        CoreProperties coreProperties = new CoreProperties();
        coreProperties.setBaseUrl("https://links.example");

        LegacyApplicationBindingService service = new LegacyApplicationBindingService(
                idGenerator,
                applicationRepository,
                domainRepository,
                quotaRepository,
                policyRepository,
                lockRepository,
                coreProperties
        );

        LegacyApplicationBindingService.LegacyBinding binding = service.ensureLegacyDefaultBinding(7L);

        assertThat(binding).isEqualTo(new LegacyApplicationBindingService.LegacyBinding(101L, 202L));

        ArgumentCaptor<Application> applicationCaptor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).insert(applicationCaptor.capture());
        assertThat(applicationCaptor.getValue().applicationKey()).isEqualTo("legacy-default");
        assertThat(applicationCaptor.getValue().displayName()).isEqualTo("Legacy Default");

        ArgumentCaptor<ApplicationQuota> quotaCaptor = ArgumentCaptor.forClass(ApplicationQuota.class);
        verify(quotaRepository).upsert(quotaCaptor.capture());
        assertThat(quotaCaptor.getValue().monthlyLinkLimit()).isEqualTo(10_000L);
        assertThat(quotaCaptor.getValue().monthlyClickLimit()).isEqualTo(1_000_000L);

        ArgumentCaptor<ApplicationPolicy> policyCaptor = ArgumentCaptor.forClass(ApplicationPolicy.class);
        verify(policyRepository).upsert(policyCaptor.capture());
        assertThat(policyCaptor.getValue().defaultDomainScope()).isEqualTo(DomainScope.APPLICATION_DEDICATED);

        ArgumentCaptor<Domain> domainCaptor = ArgumentCaptor.forClass(Domain.class);
        verify(domainRepository).insert(domainCaptor.capture());
        assertThat(domainCaptor.getValue().hostname()).isEqualTo("legacy-7.links.example");
        assertThat(domainCaptor.getValue().applicationId()).isEqualTo(101L);
        verify(lockRepository).lockTenant(7L);
    }

    @Test
    void ensureLegacyDefaultBinding_shouldReconcileCurrentPolicyAndQuotaForValidExistingBinding() {
        ApplicationRepository applications = mock(ApplicationRepository.class);
        DomainRepository domains = mock(DomainRepository.class);
        ApplicationQuotaRepository quotas = mock(ApplicationQuotaRepository.class);
        ApplicationPolicyRepository policies = mock(ApplicationPolicyRepository.class);
        LegacyBindingLockRepository locks = mock(LegacyBindingLockRepository.class);
        Application application = new Application(101L, 7L, "legacy-default", "old name", "ACTIVE", null, null);
        Domain domain = new Domain(
                202L,
                7L,
                101L,
                "legacy-7.links.example",
                DomainScope.APPLICATION_DEDICATED,
                DomainStatus.ACTIVE,
                TargetTrustClass.FIRST_PARTY,
                null,
                null
        );
        when(applications.findByTenantIdAndApplicationKey(7L, "legacy-default")).thenReturn(Optional.of(application));
        when(domains.findByTenantIdAndHostname(7L, "legacy-7.links.example")).thenReturn(Optional.of(domain));

        LegacyApplicationBindingService service = service(applications, domains, quotas, policies, locks);

        assertThat(service.ensureLegacyDefaultBinding(7L))
                .isEqualTo(new LegacyApplicationBindingService.LegacyBinding(101L, 202L));
        verify(policies).upsert(new ApplicationPolicy(
                101L,
                DomainScope.APPLICATION_DEDICATED,
                PlatformDefaults.REDIRECT_STATUS_CODE,
                PlatformDefaults.PREVIEW_ENABLED,
                null,
                null
        ));
        verify(quotas).upsert(new ApplicationQuota(
                101L,
                PlatformDefaults.MONTHLY_LINK_LIMIT,
                PlatformDefaults.MONTHLY_CLICK_LIMIT,
                null,
                null
        ));
        verify(applications, never()).insert(org.mockito.ArgumentMatchers.any());
        verify(domains, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ensureLegacyDefaultBinding_shouldRejectDisabledApplicationBeforeRepairingConfiguration() {
        ApplicationRepository applications = mock(ApplicationRepository.class);
        DomainRepository domains = mock(DomainRepository.class);
        ApplicationQuotaRepository quotas = mock(ApplicationQuotaRepository.class);
        ApplicationPolicyRepository policies = mock(ApplicationPolicyRepository.class);
        LegacyBindingLockRepository locks = mock(LegacyBindingLockRepository.class);
        when(applications.findByTenantIdAndApplicationKey(7L, "legacy-default")).thenReturn(Optional.of(
                new Application(101L, 7L, "legacy-default", "Legacy Default", "DISABLED", null, null)
        ));

        LegacyApplicationBindingService service = service(applications, domains, quotas, policies, locks);

        assertThatThrownBy(() -> service.ensureLegacyDefaultBinding(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未启用");
        verify(policies, never()).upsert(org.mockito.ArgumentMatchers.any());
        verify(quotas, never()).upsert(org.mockito.ArgumentMatchers.any());
        verify(domains, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ensureLegacyDefaultBinding_shouldRejectCrossTenantOrWrongApplicationDomain() {
        ApplicationRepository applications = mock(ApplicationRepository.class);
        DomainRepository domains = mock(DomainRepository.class);
        ApplicationQuotaRepository quotas = mock(ApplicationQuotaRepository.class);
        ApplicationPolicyRepository policies = mock(ApplicationPolicyRepository.class);
        LegacyBindingLockRepository locks = mock(LegacyBindingLockRepository.class);
        Application application = new Application(101L, 7L, "legacy-default", "Legacy Default", "ACTIVE", null, null);
        Domain crossTenant = new Domain(
                202L,
                8L,
                999L,
                "legacy-7.links.example",
                DomainScope.APPLICATION_DEDICATED,
                DomainStatus.ACTIVE,
                TargetTrustClass.FIRST_PARTY,
                null,
                null
        );
        when(applications.findByTenantIdAndApplicationKey(7L, "legacy-default")).thenReturn(Optional.of(application));
        when(domains.findByTenantIdAndHostname(7L, "legacy-7.links.example")).thenReturn(Optional.empty());
        when(domains.findByHostname("legacy-7.links.example")).thenReturn(Optional.of(crossTenant));

        LegacyApplicationBindingService service = service(applications, domains, quotas, policies, locks);

        assertThatThrownBy(() -> service.ensureLegacyDefaultBinding(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("其他租户");
        verify(domains, never()).insert(org.mockito.ArgumentMatchers.any());
        verify(policies, never()).upsert(org.mockito.ArgumentMatchers.any());
    }

    private static LegacyApplicationBindingService service(
            ApplicationRepository applications,
            DomainRepository domains,
            ApplicationQuotaRepository quotas,
            ApplicationPolicyRepository policies,
            LegacyBindingLockRepository locks
    ) {
        CoreProperties properties = new CoreProperties();
        properties.setBaseUrl("https://links.example");
        return new LegacyApplicationBindingService(
                mock(SnowflakeIdGenerator.class),
                applications,
                domains,
                quotas,
                policies,
                locks,
                properties
        );
    }
}
