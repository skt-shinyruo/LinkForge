package com.linkforge.platform.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.platform.ApplicationQuotaView;
import com.linkforge.contract.platform.DomainHostnameLookupPort;
import com.linkforge.contract.platform.LegacyApplicationBindingView;
import com.linkforge.contract.platform.LegacyApplicationProvisioningPort;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.platform.application.port.ApplicationPolicyRepository;
import com.linkforge.platform.application.port.ApplicationQuotaRepository;
import com.linkforge.platform.application.port.ApplicationRepository;
import com.linkforge.platform.application.port.DomainRepository;
import com.linkforge.platform.domain.Application;
import com.linkforge.platform.domain.ApplicationQuota;
import com.linkforge.platform.domain.Domain;
import com.linkforge.platform.domain.DomainScope;
import com.linkforge.platform.domain.DomainStatus;
import com.linkforge.platform.domain.TargetTrustClass;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformApplicationScopeAdapterTest {

    @Test
    void findApplicationQuota_shouldMapToContractView() {
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        when(applicationRepository.findByTenantIdAndId(1L, 2L))
                .thenReturn(Optional.of(new Application(2L, 1L, "app", "App", "ACTIVE", null, null)));

        ApplicationQuotaRepository quotaRepository = mock(ApplicationQuotaRepository.class);
        when(quotaRepository.findByApplicationId(2L))
                .thenReturn(Optional.of(new ApplicationQuota(2L, 100L, 200L, null, null)));

        PlatformApplicationScopeAdapter adapter = newAdapter(
                applicationRepository,
                quotaRepository,
                mock(DomainRepository.class),
                mock(ApplicationPolicyRepository.class),
                mock(SnowflakeIdGenerator.class)
        );

        assertThat(adapter.findApplicationQuota(1L, 2L))
                .contains(new ApplicationQuotaView(2L, 100L, 200L));
    }

    @Test
    void requireApplicationExists_shouldThrowWhenMissing() {
        PlatformApplicationScopeAdapter adapter = newAdapter(
                mock(ApplicationRepository.class),
                mock(ApplicationQuotaRepository.class),
                mock(DomainRepository.class),
                mock(ApplicationPolicyRepository.class),
                mock(SnowflakeIdGenerator.class)
        );

        assertThatThrownBy(() -> adapter.requireApplicationExists(1L, 2L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void ensureLegacyDefaultBinding_shouldCreateMissingApplicationAndDomain() {
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        DomainRepository domainRepository = mock(DomainRepository.class);
        ApplicationQuotaRepository quotaRepository = mock(ApplicationQuotaRepository.class);
        ApplicationPolicyRepository policyRepository = mock(ApplicationPolicyRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);

        when(idGenerator.nextId()).thenReturn(101L, 202L);
        when(applicationRepository.findByTenantIdAndApplicationKey(7L, "legacy-default")).thenReturn(Optional.empty());
        when(domainRepository.findByTenantIdAndHostname(7L, "legacy-7.links.example")).thenReturn(Optional.empty());

        PlatformApplicationScopeAdapter adapter = newAdapter(
                applicationRepository,
                quotaRepository,
                domainRepository,
                policyRepository,
                idGenerator
        );

        LegacyApplicationBindingView binding = adapter.ensureLegacyDefaultBinding(
                7L,
                "legacy-default",
                "Legacy Default",
                "legacy-7.links.example",
                10L,
                20L
        );

        assertThat(binding).isEqualTo(new LegacyApplicationBindingView(101L, 202L));
        verify(applicationRepository).insert(any());
        verify(policyRepository).insert(any());
        verify(quotaRepository).insert(any());
        verify(domainRepository).insert(any());
    }

    @Test
    void findDomainHostname_shouldExposeOnlyStableHostnameView() {
        DomainRepository domainRepository = mock(DomainRepository.class);
        when(domainRepository.findByTenantIdAndId(1L, 3L))
                .thenReturn(Optional.of(new Domain(3L, 1L, 2L, "d.example", DomainScope.TENANT_SHARED, DomainStatus.ACTIVE, TargetTrustClass.FIRST_PARTY, null, null)));

        PlatformApplicationScopeAdapter adapter = newAdapter(
                mock(ApplicationRepository.class),
                mock(ApplicationQuotaRepository.class),
                domainRepository,
                mock(ApplicationPolicyRepository.class),
                mock(SnowflakeIdGenerator.class)
        );

        assertThat(adapter.findDomainHostname(1L, 3L)).contains("d.example");
    }

    private static PlatformApplicationScopeAdapter newAdapter(
            ApplicationRepository applicationRepository,
            ApplicationQuotaRepository applicationQuotaRepository,
            DomainRepository domainRepository,
            ApplicationPolicyRepository applicationPolicyRepository,
            SnowflakeIdGenerator idGenerator
    ) {
        return new PlatformApplicationScopeAdapter(
                applicationRepository,
                domainRepository,
                applicationQuotaRepository,
                applicationPolicyRepository,
                idGenerator
        );
    }
}
