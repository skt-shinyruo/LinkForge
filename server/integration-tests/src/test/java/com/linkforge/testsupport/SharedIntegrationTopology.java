package com.linkforge.testsupport;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * opt-in 集成测试在 JVM 内共享的 MySQL 主库、副本与 Redis 拓扑。
 *
 * <p>该拓扑拥有容器启动、Flyway migration 和连接属性。测试隔离是独立操作：
 * {@link #resetFixtures()} 删除两个数据库中非 Flyway 表的全部数据并执行 Redis {@code FLUSHALL}，
 * 同时清除 stream、consumer group、pending entry 和 TTL 状态。</p>
 */
public final class SharedIntegrationTopology {

    private static final String MYSQL_IMAGE = "mysql:8.0.36";
    private static final String REDIS_IMAGE = "redis:8.6.2-alpine";
    private static final String SHARDINGSPHERE_URL =
            "jdbc:shardingsphere:classpath:shardingsphere-readwrite.yaml?placeholder-type=system_props";
    private static final List<String> BUSINESS_AUTO_INCREMENT_TABLES = List.of(
            "integration_events",
            "redirect_cache_invalidation_outbox"
    );

    private static final MySQLContainer<?> PRIMARY = mysql("linkforge_shared_primary");
    private static final MySQLContainer<?> REPLICA = mysql("linkforge_shared_replica");
    private static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1)
                    .withStartupTimeout(Duration.ofSeconds(120)))
            .withStartupAttempts(3);

    private static final Object START_MONITOR = new Object();
    private static final AtomicInteger START_ATTEMPTS = new AtomicInteger();
    private static final AtomicLong RESET_COUNT = new AtomicLong();
    private static final AtomicLong RESET_NANOS = new AtomicLong();

    private static volatile boolean started;
    private static volatile long startupMillis;

    private SharedIntegrationTopology() {
    }

    /** 并行启动全部容器，并在每个测试 JVM 内只迁移两个数据库一次。 */
    public static void ensureStarted() {
        if (started) {
            return;
        }
        synchronized (START_MONITOR) {
            if (started) {
                return;
            }
            long startedAt = System.nanoTime();
            START_ATTEMPTS.incrementAndGet();
            try {
                Startables.deepStart(Stream.of(PRIMARY, REPLICA, REDIS)).join();
                migrate(PRIMARY);
                migrate(REPLICA);
                configureReadWriteSystemProperties();
                startupMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
                started = true;
            } catch (RuntimeException ex) {
                throw new IllegalStateException(
                        "Failed to start shared integration topology (primary=" + PRIMARY.getDockerImageName()
                                + ", replica=" + REPLICA.getDockerImageName()
                                + ", redis=" + REDIS.getDockerImageName() + ")",
                        ex
                );
            }
        }
    }

    /** 注册只含主库的数据源与共享 Redis 端点。 */
    public static void registerPrimaryProperties(DynamicPropertyRegistry registry) {
        ensureStarted();
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.url", PRIMARY::getJdbcUrl);
        registry.add("spring.datasource.username", PRIMARY::getUsername);
        registry.add("spring.datasource.password", PRIMARY::getPassword);
        registerFlywayAndRedis(registry);
    }

    /** 注册使用独立主库与刻意保持延迟副本的 ShardingSphere。 */
    public static void registerReadWriteProperties(DynamicPropertyRegistry registry) {
        ensureStarted();
        registry.add("spring.datasource.driver-class-name", () ->
                "org.apache.shardingsphere.driver.ShardingSphereDriver");
        registry.add("spring.datasource.url", () -> SHARDINGSPHERE_URL);
        registerFlywayAndRedis(registry);
        registry.add("app.edge.risk-control.enabled", () -> "false");
    }

    /**
     * 清除所有可变 fixture 状态，同时保留 Flyway 历史与数据库结构。
     * 调用方必须通过 {@link SharedIntegrationFixtureExtension} 将该操作与测试执行串行化。
     */
    public static void resetFixtures() {
        ensureStarted();
        long startedAt = System.nanoTime();
        clearBusinessTables(PRIMARY);
        clearBusinessTables(REPLICA);
        flushRedis();
        RESET_NANOS.addAndGet(System.nanoTime() - startedAt);
        RESET_COUNT.incrementAndGet();
    }

    public static MySQLContainer<?> primary() {
        ensureStarted();
        return PRIMARY;
    }

    public static MySQLContainer<?> replica() {
        ensureStarted();
        return REPLICA;
    }

    public static GenericContainer<?> redis() {
        ensureStarted();
        return REDIS;
    }

    public static JdbcTemplate primaryJdbc() {
        return jdbc(PRIMARY);
    }

    public static JdbcTemplate replicaJdbc() {
        return jdbc(REPLICA);
    }

    /** 集成门禁用于报告启动与 reset 成本的快照。 */
    public static Metrics metrics() {
        long count = RESET_COUNT.get();
        long nanos = RESET_NANOS.get();
        return new Metrics(
                START_ATTEMPTS.get(),
                started ? 3 : 0,
                startupMillis,
                count,
                count == 0 ? 0 : Duration.ofNanos(nanos / count).toMillis()
        );
    }

    private static void registerFlywayAndRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", PRIMARY::getJdbcUrl);
        registry.add("spring.flyway.user", PRIMARY::getUsername);
        registry.add("spring.flyway.password", PRIMARY::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.security.jwt.secret", () ->
                "test-secret-please-change-but-long-enough-32-bytes");
        registry.add("app.analytics.salt", () -> "test-analytics-salt");
        registry.add("app.scheduling.enabled", () -> "false");
    }

    private static void configureReadWriteSystemProperties() {
        System.setProperty("DB_WRITE_URL", PRIMARY.getJdbcUrl());
        System.setProperty("DB_WRITE_USERNAME", PRIMARY.getUsername());
        System.setProperty("DB_WRITE_PASSWORD", PRIMARY.getPassword());
        System.setProperty("DB_READ_URL", REPLICA.getJdbcUrl());
        System.setProperty("DB_READ_USERNAME", REPLICA.getUsername());
        System.setProperty("DB_READ_PASSWORD", REPLICA.getPassword());
    }

    private static MySQLContainer<?> mysql(String databaseName) {
        return new MySQLContainer<>(MYSQL_IMAGE)
                .withDatabaseName(databaseName)
                .withUsername("linkforge")
                .withPassword("linkforge");
    }

    private static void migrate(MySQLContainer<?> mysql) {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static JdbcTemplate jdbc(MySQLContainer<?> mysql) {
        ensureStarted();
        return new JdbcTemplate(new DriverManagerDataSource(
                mysql.getJdbcUrl(),
                mysql.getUsername(),
                mysql.getPassword()
        ));
    }

    private static void clearBusinessTables(MySQLContainer<?> mysql) {
        try (Connection connection = java.sql.DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             Statement statement = connection.createStatement()) {
            List<String> tables = new ArrayList<>();
            List<String> autoIncrementTablesToReset = new ArrayList<>();
            statement.execute("SET SESSION information_schema_stats_expiry = 0");
            try (ResultSet result = statement.executeQuery("""
                    SELECT table_name, auto_increment
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_type = 'BASE TABLE'
                      AND table_name <> 'flyway_schema_history'
                    """)) {
                while (result.next()) {
                    String table = result.getString(1);
                    tables.add(table);
                    long nextId = result.getLong(2);
                    if (BUSINESS_AUTO_INCREMENT_TABLES.contains(table) && !result.wasNull() && nextId > 1L) {
                        autoIncrementTablesToReset.add(table);
                    }
                }
            }
            connection.setAutoCommit(false);
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                for (String table : tables) {
                    statement.addBatch("DELETE FROM `" + table.replace("`", "``") + "`");
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            // MySQL DDL 会隐式提交，因此只能在删除事务提交后重置两条业务自增序列。
            connection.setAutoCommit(true);
            resetBusinessAutoIncrementSequences(statement, autoIncrementTablesToReset);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to reset shared MySQL fixture " + mysql.getDatabaseName(), ex);
        }
    }

    private static void resetBusinessAutoIncrementSequences(Statement statement, List<String> tables) throws SQLException {
        for (String table : tables) {
            statement.execute("ALTER TABLE `" + table + "` AUTO_INCREMENT = 1");
        }
    }

    private static void flushRedis() {
        try {
            Container.ExecResult result = REDIS.execInContainer("redis-cli", "FLUSHALL");
            if (result.getExitCode() != 0 || !result.getStdout().contains("OK")) {
                throw new IllegalStateException(
                        "redis-cli FLUSHALL failed: exit=" + result.getExitCode() + ", stderr=" + result.getStderr()
                );
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to reset shared Redis fixture", ex);
        }
    }

    public record Metrics(
            int topologyStartAttempts,
            int containerStartCount,
            long startupMillis,
            long resetCount,
            long averageResetMillis
    ) {
    }
}
