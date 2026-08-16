package com.linkforge.testsupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

/** 需要观察独立主库与延迟副本路由的 opt-in 集成测试基类。 */
@ResourceLock("shared-integration-fixture")
public abstract class SharedReadWriteIntegrationTestSupport {

    protected static final MySQLContainer<?> PRIMARY = SharedIntegrationTopology.primary();
    protected static final MySQLContainer<?> REPLICA = SharedIntegrationTopology.replica();
    protected static final GenericContainer<?> REDIS = SharedIntegrationTopology.redis();

    @BeforeEach
    protected void resetSharedFixtures() {
        SharedIntegrationTopology.resetFixtures();
    }

    @DynamicPropertySource
    protected static void sharedReadWriteIntegrationProperties(DynamicPropertyRegistry registry) {
        SharedIntegrationTopology.registerReadWriteProperties(registry);
    }
}
