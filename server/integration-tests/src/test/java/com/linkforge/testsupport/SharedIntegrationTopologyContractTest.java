package com.linkforge.testsupport;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SharedIntegrationTopologyContractTest {

    private static final Path TEST_SOURCES = Path.of("src/test/java");
    private static final String TOPOLOGY_SOURCE =
            "src/test/java/com/linkforge/testsupport/SharedIntegrationTopology.java";
    private static final Path METRICS_LISTENER_SERVICE = Path.of(
            "src/test/resources/META-INF/services/org.junit.platform.launcher.LauncherSessionListener"
    );

    @Test
    void equivalentMysqlAndRedisContainers_shouldOnlyBeConstructedBySharedTopology() throws IOException {
        List<String> containerOwners;
        try (var sources = Files.walk(TEST_SOURCES)) {
            containerOwners = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(SharedIntegrationTopologyContractTest::constructsMysqlOrRedisContainer)
                    .map(path -> path.toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }

        assertThat(containerOwners).containsExactly(TOPOLOGY_SOURCE);
    }

    @Test
    void topologyMetrics_shouldBePublishedAtLauncherSessionClose() throws IOException {
        assertThat(METRICS_LISTENER_SERVICE).exists();
        assertThat(Files.readString(METRICS_LISTENER_SERVICE).trim())
                .isEqualTo("com.linkforge.testsupport.SharedIntegrationTopologyMetricsListener");
    }

    private static boolean constructsMysqlOrRedisContainer(Path source) {
        try {
            String java = Files.readString(source);
            return java.contains("new " + "MySQLContainer") || java.contains("new " + "GenericContainer");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to inspect integration test source " + source, ex);
        }
    }
}
