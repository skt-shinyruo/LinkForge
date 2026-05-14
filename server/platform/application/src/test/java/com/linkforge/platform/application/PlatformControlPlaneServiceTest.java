package com.linkforge.platform.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.platform.application.port.ApplicationQuotaRepository;
import com.linkforge.platform.application.port.ApplicationRepository;
import com.linkforge.platform.application.port.DomainRepository;
import com.linkforge.platform.domain.Application;
import com.linkforge.platform.domain.Domain;
import com.linkforge.platform.domain.DomainScope;
import com.linkforge.platform.domain.DomainStatus;
import com.linkforge.platform.domain.TargetTrustClass;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformControlPlaneServiceTest {

    @Test
    void requireApplicationAndDomainAuthorized_shouldTranslateInactiveDomainReason() {
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        DomainRepository domainRepository = mock(DomainRepository.class);
        PlatformControlPlaneService service = new PlatformControlPlaneService(
                mock(ApplicationProvisioningService.class),
                applicationRepository,
                mock(ApplicationQuotaRepository.class),
                domainRepository
        );
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
        when(domainRepository.isApplicationAuthorizedForDomain(2001L, 3001L)).thenReturn(true);

        assertThatThrownBy(() -> service.requireApplicationAndDomainAuthorized(1L, 2001L, 3001L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("域名未启用");
    }

    @Test
    void requireApplicationAndDomainAuthorized_shouldRejectDisabledApplication() {
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        PlatformControlPlaneService service = new PlatformControlPlaneService(
                mock(ApplicationProvisioningService.class),
                applicationRepository,
                mock(ApplicationQuotaRepository.class),
                mock(DomainRepository.class)
        );
        when(applicationRepository.findByTenantIdAndId(1L, 2001L))
                .thenReturn(Optional.of(new Application(2001L, 1L, "api", "API", "DISABLED", null, null)));

        assertThatThrownBy(() -> service.requireApplicationAndDomainAuthorized(1L, 2001L, 3001L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用未启用");
    }

    @Test
    void requireApplicationExists_shouldRejectDisabledApplication() {
        ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
        PlatformControlPlaneService service = new PlatformControlPlaneService(
                mock(ApplicationProvisioningService.class),
                applicationRepository,
                mock(ApplicationQuotaRepository.class),
                mock(DomainRepository.class)
        );
        when(applicationRepository.findByTenantIdAndId(1L, 2001L))
                .thenReturn(Optional.of(new Application(2001L, 1L, "api", "API", "DISABLED", null, null)));

        assertThatThrownBy(() -> service.requireApplicationExists(1L, 2001L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用未启用");
    }
}
