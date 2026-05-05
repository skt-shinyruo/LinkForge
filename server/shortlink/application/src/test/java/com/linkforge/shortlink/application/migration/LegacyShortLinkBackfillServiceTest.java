package com.linkforge.shortlink.application.migration;

import com.linkforge.contract.platform.LegacyApplicationBindingView;
import com.linkforge.contract.platform.LegacyApplicationProvisioningPort;
import com.linkforge.shortlink.application.port.ShortLinkOwnershipBackfillRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyShortLinkBackfillServiceTest {

    @Test
    void constructor_shouldDependOnLegacyPlatformContract_insteadOfPlatformRepositories() {
        Constructor<?> constructor = LegacyShortLinkBackfillService.class.getDeclaredConstructors()[0];

        assertThat(Arrays.stream(constructor.getParameterTypes()).map(Class::getName))
                .contains("com.linkforge.contract.platform.LegacyApplicationProvisioningPort")
                .doesNotContain("com.linkforge.foundation.config.CoreProperties")
                .doesNotContain("com.linkforge.platform.application.port.ApplicationRepository")
                .doesNotContain("com.linkforge.platform.application.port.DomainRepository")
                .doesNotContain("com.linkforge.platform.application.port.ApplicationQuotaRepository")
                .doesNotContain("com.linkforge.platform.application.port.ApplicationPolicyRepository");
    }

    @Test
    void backfillTenant_shouldUseLegacyBindingFromPlatformContract() {
        LegacyApplicationProvisioningPort provisioningPort = mock(LegacyApplicationProvisioningPort.class);
        when(provisioningPort.ensureLegacyDefaultBinding(9L))
                .thenReturn(new LegacyApplicationBindingView(101L, 202L));

        ShortLinkOwnershipBackfillRepository backfillRepository = mock(ShortLinkOwnershipBackfillRepository.class);
        when(backfillRepository.backfillTenant(9L, 101L, 202L)).thenReturn(7);

        LegacyShortLinkBackfillService service = new LegacyShortLinkBackfillService(
                provisioningPort,
                backfillRepository
        );

        BackfillResult result = service.backfillTenant(9L);

        assertThat(result).isEqualTo(new BackfillResult(9L, 101L, 202L, 7));
        verify(backfillRepository).backfillTenant(9L, 101L, 202L);
        verify(provisioningPort).ensureLegacyDefaultBinding(9L);
    }
}
