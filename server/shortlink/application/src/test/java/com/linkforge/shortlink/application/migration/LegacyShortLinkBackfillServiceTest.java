package com.linkforge.shortlink.application.migration;

import com.linkforge.contract.platform.LegacyApplicationBindingView;
import com.linkforge.contract.platform.LegacyApplicationProvisioningPort;
import com.linkforge.foundation.config.CoreProperties;
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
                .doesNotContain("com.linkforge.platform.application.port.ApplicationRepository")
                .doesNotContain("com.linkforge.platform.application.port.DomainRepository")
                .doesNotContain("com.linkforge.platform.application.port.ApplicationQuotaRepository")
                .doesNotContain("com.linkforge.platform.application.port.ApplicationPolicyRepository");
    }

    @Test
    void backfillTenant_shouldUseLegacyBindingFromPlatformContract() {
        LegacyApplicationProvisioningPort provisioningPort = mock(LegacyApplicationProvisioningPort.class);
        when(provisioningPort.ensureLegacyDefaultBinding(
                9L,
                LegacyShortLinkBackfillService.LEGACY_DEFAULT_APPLICATION_KEY,
                LegacyShortLinkBackfillService.LEGACY_DEFAULT_APPLICATION_NAME,
                "legacy-9.links.example",
                LegacyShortLinkBackfillService.DEFAULT_MONTHLY_LINK_LIMIT,
                LegacyShortLinkBackfillService.DEFAULT_MONTHLY_CLICK_LIMIT
        )).thenReturn(new LegacyApplicationBindingView(101L, 202L));

        ShortLinkOwnershipBackfillRepository backfillRepository = mock(ShortLinkOwnershipBackfillRepository.class);
        when(backfillRepository.backfillTenant(9L, 101L, 202L)).thenReturn(7);

        CoreProperties coreProperties = new CoreProperties();
        coreProperties.setBaseUrl("https://links.example");

        LegacyShortLinkBackfillService service = new LegacyShortLinkBackfillService(
                provisioningPort,
                backfillRepository,
                coreProperties
        );

        LegacyShortLinkBackfillService.BackfillResult result = service.backfillTenant(9L);

        assertThat(result).isEqualTo(new LegacyShortLinkBackfillService.BackfillResult(9L, 101L, 202L, 7));
        verify(backfillRepository).backfillTenant(9L, 101L, 202L);
    }
}
