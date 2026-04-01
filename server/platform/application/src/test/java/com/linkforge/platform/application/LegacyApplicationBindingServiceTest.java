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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(101L, 202L);
        when(applicationRepository.findByTenantIdAndApplicationKey(7L, "legacy-default")).thenReturn(Optional.empty());
        when(domainRepository.findByTenantIdAndHostname(7L, "legacy-7.links.example")).thenReturn(Optional.empty());

        CoreProperties coreProperties = new CoreProperties();
        coreProperties.setBaseUrl("https://links.example");

        LegacyApplicationBindingService service = new LegacyApplicationBindingService(
                idGenerator,
                applicationRepository,
                domainRepository,
                quotaRepository,
                policyRepository,
                coreProperties
        );

        LegacyApplicationBindingService.LegacyBinding binding = service.ensureLegacyDefaultBinding(7L);

        assertThat(binding).isEqualTo(new LegacyApplicationBindingService.LegacyBinding(101L, 202L));

        ArgumentCaptor<Application> applicationCaptor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).insert(applicationCaptor.capture());
        assertThat(applicationCaptor.getValue().applicationKey()).isEqualTo("legacy-default");
        assertThat(applicationCaptor.getValue().displayName()).isEqualTo("Legacy Default");

        ArgumentCaptor<ApplicationQuota> quotaCaptor = ArgumentCaptor.forClass(ApplicationQuota.class);
        verify(quotaRepository).insert(quotaCaptor.capture());
        assertThat(quotaCaptor.getValue().monthlyLinkLimit()).isEqualTo(10_000L);
        assertThat(quotaCaptor.getValue().monthlyClickLimit()).isEqualTo(1_000_000L);

        ArgumentCaptor<ApplicationPolicy> policyCaptor = ArgumentCaptor.forClass(ApplicationPolicy.class);
        verify(policyRepository).insert(policyCaptor.capture());
        assertThat(policyCaptor.getValue().defaultDomainScope()).isEqualTo(DomainScope.APPLICATION_DEDICATED);

        ArgumentCaptor<Domain> domainCaptor = ArgumentCaptor.forClass(Domain.class);
        verify(domainRepository).insert(domainCaptor.capture());
        assertThat(domainCaptor.getValue().hostname()).isEqualTo("legacy-7.links.example");
        assertThat(domainCaptor.getValue().applicationId()).isEqualTo(101L);
    }
}
