package com.linkforge.testsupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SharedIntegrationTopologyConcurrencyTest {

    @AfterAll
    static void resetFixtures() {
        SharedIntegrationTopology.resetFixtures();
    }

    @RepeatedTest(3)
    void concurrentAndRepeatedAccess_shouldUseOneTopologyStart() throws Exception {
        int callers = 12;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        List<Future<Object[]>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(30, TimeUnit.SECONDS)).isTrue();
                    SharedIntegrationTopology.ensureStarted();
                    return new Object[]{
                            SharedIntegrationTopology.primary(),
                            SharedIntegrationTopology.replica(),
                            SharedIntegrationTopology.redis()
                    };
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Object[] first = futures.get(0).get(120, TimeUnit.SECONDS);
            for (Future<Object[]> future : futures) {
                Object[] topology = future.get(120, TimeUnit.SECONDS);
                assertThat(topology[0]).isSameAs(first[0]);
                assertThat(topology[1]).isSameAs(first[1]);
                assertThat(topology[2]).isSameAs(first[2]);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        SharedIntegrationTopology.Metrics metrics = SharedIntegrationTopology.metrics();
        assertThat(metrics.topologyStartAttempts()).isOne();
        assertThat(metrics.containerStartCount()).isEqualTo(3);
    }
}
