package com.linkforge.testsupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

/** 使用单一主库数据源与共享 Redis 的 opt-in 集成测试基类。 */
@ResourceLock("shared-integration-fixture")
public abstract class SharedIntegrationTestSupport {

    protected static final MySQLContainer<?> MYSQL = SharedIntegrationTopology.primary();
    protected static final GenericContainer<?> REDIS = SharedIntegrationTopology.redis();

    @BeforeEach
    protected void resetSharedFixtures() {
        SharedIntegrationTopology.resetFixtures();
    }

    @DynamicPropertySource
    protected static void sharedIntegrationProperties(DynamicPropertyRegistry registry) {
        SharedIntegrationTopology.registerPrimaryProperties(registry);
    }

    /** 让与持久化测试无关的 Analytics 调度与投影保持停用。 */
    protected static void registerDisabledAnalytics(DynamicPropertyRegistry registry) {
        registry.add("app.analytics.dimensions.enabled", () -> "false");
        registry.add("app.analytics.events.enabled", () -> "false");
        registry.add("app.analytics.events.sample-rate", () -> "1");
        registry.add("APP_ANALYTICS_EVENT_INGEST_DELAY_MS", () -> "9999999");
        registry.add("APP_ANALYTICS_EVENT_RETENTION_DELAY_MS", () -> "9999999");
        registry.add("APP_ANALYTICS_DIM_FLUSH_DELAY_MS", () -> "9999999");
        registry.add("APP_ANALYTICS_FLUSH_DELAY_MS", () -> "9999999");
    }
}
