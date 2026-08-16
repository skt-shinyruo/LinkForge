package com.linkforge.shortlink.application.migration;

import com.linkforge.contract.platform.LegacyApplicationBindingView;
import com.linkforge.contract.platform.LegacyApplicationProvisioningPort;
import com.linkforge.shortlink.application.port.LegacyShortLinkBackfillStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyShortLinkBackfillServiceTest {

    @Test
    void constructor_shouldDependOnlyOnPublishedBindingCheckpointStoreAndPublicReconciliationUseCase() {
        Constructor<?> constructor = LegacyShortLinkBackfillService.class.getDeclaredConstructors()[0];

        assertThat(Arrays.stream(constructor.getParameterTypes()).map(Class::getName))
                .containsExactly(
                        "com.linkforge.contract.platform.LegacyApplicationProvisioningPort",
                        "com.linkforge.shortlink.application.port.LegacyShortLinkBackfillStore",
                        "com.linkforge.shortlink.application.migration.ShortLinkOwnershipReconciliationService"
                )
                .doesNotContain("com.linkforge.shortlink.application.port.ShortLinkOwnershipBackfillRepository");
    }

    @Test
    void reconcileNextBatch_shouldDispatchEveryItemThroughPublicReconciliationUseCase() {
        LegacyApplicationProvisioningPort provisioningPort = mock(LegacyApplicationProvisioningPort.class);
        when(provisioningPort.ensureLegacyDefaultBinding(9L))
                .thenReturn(new LegacyApplicationBindingView(101L, 202L));
        LegacyShortLinkBackfillStore store = mock(LegacyShortLinkBackfillStore.class);
        when(store.takeBatch(9L, 101L, 202L, 2)).thenReturn(List.of(
                new LegacyShortLinkBackfillStore.WorkItem(9L, 301L, 101L, 202L),
                new LegacyShortLinkBackfillStore.WorkItem(9L, 302L, 101L, 202L)
        ));
        LegacyShortLinkBackfillProgress progress = new LegacyShortLinkBackfillProgress(
                302L, true, 0L, 1L, 0L, 1L, 0L, 0L
        );
        when(store.progress(9L)).thenReturn(progress);
        ShortLinkOwnershipReconciliationService reconciliationService = mock(ShortLinkOwnershipReconciliationService.class);
        when(reconciliationService.reconcile(9L, 301L, 101L, 202L)).thenReturn(
                new ShortLinkOwnershipReconciliationResult(
                        301L,
                        ShortLinkOwnershipReconciliationResult.Status.RECONCILED,
                        1L
                )
        );
        when(reconciliationService.reconcile(9L, 302L, 101L, 202L)).thenReturn(
                new ShortLinkOwnershipReconciliationResult(
                        302L,
                        ShortLinkOwnershipReconciliationResult.Status.RETRYABLE_CONFLICT,
                        0L
                )
        );
        LegacyShortLinkBackfillService service = new LegacyShortLinkBackfillService(
                provisioningPort,
                store,
                reconciliationService
        );

        LegacyShortLinkBackfillBatchResult result = service.reconcileNextBatch(9L, 2);

        assertThat(result.attemptedCount()).isEqualTo(2);
        assertThat(result.reconciledCount()).isEqualTo(1);
        assertThat(result.retryableCount()).isEqualTo(1);
        assertThat(result.progress()).isSameAs(progress);
        verify(store).recordOutcome(9L, 301L, LegacyShortLinkBackfillStore.Outcome.RECONCILED, null);
        verify(store).recordOutcome(
                9L,
                302L,
                LegacyShortLinkBackfillStore.Outcome.RETRYABLE,
                "optimistic ownership conflict"
        );
    }
}
