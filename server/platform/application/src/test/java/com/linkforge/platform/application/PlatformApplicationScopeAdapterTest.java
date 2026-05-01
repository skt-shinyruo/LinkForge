package com.linkforge.platform.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.platform.ApplicationQuotaView;
import com.linkforge.contract.platform.LegacyApplicationBindingView;
import com.linkforge.platform.application.port.DomainRepository;
import com.linkforge.platform.domain.ApplicationQuota;
import com.linkforge.platform.domain.Domain;
import com.linkforge.platform.domain.DomainScope;
import com.linkforge.platform.domain.DomainStatus;
import com.linkforge.platform.domain.TargetTrustClass;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformApplicationScopeAdapterTest {

    @Test
    void findApplicationQuota_shouldDelegateToControlPlaneAndMapToContractView() {
        PlatformControlPlaneService controlPlaneService = mock(PlatformControlPlaneService.class);
        when(controlPlaneService.findApplicationQuota(1L, 2L))
                .thenReturn(Optional.of(new ApplicationQuota(2L, 100L, 200L, null, null)));

        PlatformApplicationScopeAdapter adapter = newAdapter(
                controlPlaneService,
                mock(DomainRepository.class),
                mock(LegacyApplicationBindingService.class)
        );

        assertThat(adapter.findApplicationQuota(1L, 2L))
                .contains(new ApplicationQuotaView(2L, 100L, 200L));
        verify(controlPlaneService).findApplicationQuota(1L, 2L);
    }

    @Test
    void requireApplicationExists_shouldThrowWhenMissing() {
        PlatformControlPlaneService controlPlaneService = mock(PlatformControlPlaneService.class);
        doThrow(new BusinessException(com.linkforge.contract.api.ErrorCode.NOT_FOUND, "应用不存在"))
                .when(controlPlaneService).requireApplicationExists(1L, 2L);

        PlatformApplicationScopeAdapter adapter = newAdapter(
                controlPlaneService,
                mock(DomainRepository.class),
                mock(LegacyApplicationBindingService.class)
        );

        assertThatThrownBy(() -> adapter.requireApplicationExists(1L, 2L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void requireApplicationAndDomainAuthorized_shouldDelegateToControlPlane() {
        PlatformControlPlaneService controlPlaneService = mock(PlatformControlPlaneService.class);

        PlatformApplicationScopeAdapter adapter = newAdapter(
                controlPlaneService,
                mock(DomainRepository.class),
                mock(LegacyApplicationBindingService.class)
        );

        adapter.requireApplicationAndDomainAuthorized(1L, 2L, 3L);

        verify(controlPlaneService).requireApplicationAndDomainAuthorized(1L, 2L, 3L);
    }

    @Test
    void ensureLegacyDefaultBinding_shouldDelegateToLegacyBindingService() {
        DomainRepository domainRepository = mock(DomainRepository.class);
        LegacyApplicationBindingService legacyBindingService = mock(LegacyApplicationBindingService.class);
        when(legacyBindingService.ensureLegacyDefaultBinding(7L))
                .thenReturn(new LegacyApplicationBindingService.LegacyBinding(101L, 202L));

        PlatformApplicationScopeAdapter adapter = newAdapter(
                mock(PlatformControlPlaneService.class),
                domainRepository,
                legacyBindingService
        );

        LegacyApplicationBindingView binding = adapter.ensureLegacyDefaultBinding(7L);

        assertThat(binding).isEqualTo(new LegacyApplicationBindingView(101L, 202L));
        verify(legacyBindingService).ensureLegacyDefaultBinding(7L);
    }

    @Test
    void findDomainHostname_shouldExposeOnlyStableHostnameView() {
        DomainRepository domainRepository = mock(DomainRepository.class);
        when(domainRepository.findByTenantIdAndId(1L, 3L))
                .thenReturn(Optional.of(new Domain(3L, 1L, 2L, "d.example", DomainScope.TENANT_SHARED, DomainStatus.ACTIVE, TargetTrustClass.FIRST_PARTY, null, null)));

        PlatformApplicationScopeAdapter adapter = newAdapter(
                mock(PlatformControlPlaneService.class),
                domainRepository,
                mock(LegacyApplicationBindingService.class)
        );

        assertThat(adapter.findDomainHostname(1L, 3L)).contains("d.example");
    }

    @Test
    void findDomainIdByHostname_shouldExposeStableDomainIdentifier() {
        DomainRepository domainRepository = mock(DomainRepository.class);
        when(domainRepository.findByTenantIdAndHostname(1L, "d.example"))
                .thenReturn(Optional.of(new Domain(3L, 1L, 2L, "d.example", DomainScope.TENANT_SHARED, DomainStatus.ACTIVE, TargetTrustClass.FIRST_PARTY, null, null)));

        PlatformApplicationScopeAdapter adapter = newAdapter(
                mock(PlatformControlPlaneService.class),
                domainRepository,
                mock(LegacyApplicationBindingService.class)
        );

        assertThat(adapter.findDomainIdByHostname(1L, "d.example")).contains(3L);
    }

    private static PlatformApplicationScopeAdapter newAdapter(
            PlatformControlPlaneService controlPlaneService,
            DomainRepository domainRepository,
            LegacyApplicationBindingService legacyApplicationBindingService
    ) {
        return new PlatformApplicationScopeAdapter(
                controlPlaneService,
                domainRepository,
                legacyApplicationBindingService
        );
    }
}
