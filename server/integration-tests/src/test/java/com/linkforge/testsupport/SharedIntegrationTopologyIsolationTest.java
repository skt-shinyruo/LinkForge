package com.linkforge.testsupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.Container;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SharedIntegrationFixtureExtension.class)
class SharedIntegrationTopologyIsolationTest {

    private static final String SENTINEL_TABLE = "shared_fixture_sentinel";

    @BeforeAll
    static void createSentinelTable() {
        SharedIntegrationTopology.primaryJdbc().execute("""
                CREATE TABLE IF NOT EXISTS shared_fixture_sentinel (
                    id VARCHAR(64) PRIMARY KEY,
                    marker VARCHAR(128) NOT NULL
                )
                """);
    }

    @AfterAll
    static void dropSentinelTable() {
        SharedIntegrationTopology.primaryJdbc().execute("DROP TABLE IF EXISTS " + SENTINEL_TABLE);
    }

    @Test
    void fixtureBoundary_shouldStartWithoutPreviousMysqlOrRedisSentinels() throws Exception {
        String marker = "first-" + UUID.randomUUID();
        assertFixtureIsEmpty();

        SharedIntegrationTopology.primaryJdbc().update(
                "INSERT INTO " + SENTINEL_TABLE + " (id, marker) VALUES (?, ?)", marker, marker
        );
        redis("SET", "fixture:string", marker);

        assertThat(sentinelCount()).isOne();
        assertThat(redis("DBSIZE")).isEqualTo("1");
    }

    @RepeatedTest(2)
    void repeatedFixture_shouldNotLeakMysqlRedisStreamGroupOrTtlState() throws Exception {
        String marker = "repeat-" + UUID.randomUUID();
        assertFixtureIsEmpty();

        SharedIntegrationTopology.primaryJdbc().update(
                "INSERT INTO " + SENTINEL_TABLE + " (id, marker) VALUES (?, ?)", marker, marker
        );
        redis("HSET", "fixture:hash", "field", marker);
        redis("PFADD", "fixture:hll", marker);
        redis("SADD", "fixture:set", marker);
        redis("SETEX", "fixture:ttl", "60", marker);
        redis("XADD", "fixture:stream", "*", "marker", marker);
        redis("XGROUP", "CREATE", "fixture:stream", "fixture-group", "0");
        redis("XREADGROUP", "GROUP", "fixture-group", "fixture-consumer", "COUNT", "1",
                "STREAMS", "fixture:stream", ">");

        SharedIntegrationTopology.resetFixtures();

        assertFixtureIsEmpty();
    }

    @Test
    void topologyMetrics_shouldExposeBoundedContainerAndResetCosts() {
        SharedIntegrationTopology.Metrics metrics = SharedIntegrationTopology.metrics();

        assertThat(metrics.topologyStartAttempts()).isOne();
        assertThat(metrics.containerStartCount()).isEqualTo(3);
        assertThat(metrics.startupMillis()).isPositive();
        assertThat(metrics.resetCount()).isPositive();
        assertThat(metrics.averageResetMillis()).isNotNegative();
    }

    @Test
    void consecutiveFixtureBoundaries_shouldRestartBusinessAutoIncrementIdsDeterministically() {
        AutoIncrementIds firstPrimary = insertAutoIncrementFixtures(
                SharedIntegrationTopology.primaryJdbc(),
                "primary"
        );
        AutoIncrementIds firstReplica = insertAutoIncrementFixtures(
                SharedIntegrationTopology.replicaJdbc(),
                "replica"
        );

        SharedIntegrationTopology.resetFixtures();

        AutoIncrementIds secondPrimary = insertAutoIncrementFixtures(
                SharedIntegrationTopology.primaryJdbc(),
                "primary"
        );
        AutoIncrementIds secondReplica = insertAutoIncrementFixtures(
                SharedIntegrationTopology.replicaJdbc(),
                "replica"
        );

        assertThat(firstPrimary).isEqualTo(new AutoIncrementIds(1L, 1L));
        assertThat(firstReplica).isEqualTo(new AutoIncrementIds(1L, 1L));
        assertThat(secondPrimary).isEqualTo(firstPrimary);
        assertThat(secondReplica).isEqualTo(firstReplica);
    }

    private static void assertFixtureIsEmpty() throws Exception {
        assertThat(sentinelCount()).isZero();
        assertThat(redis("DBSIZE")).isEqualTo("0");
    }

    private static int sentinelCount() {
        JdbcTemplate jdbc = SharedIntegrationTopology.primaryJdbc();
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + SENTINEL_TABLE, Integer.class);
        return count == null ? 0 : count;
    }

    private static AutoIncrementIds insertAutoIncrementFixtures(JdbcTemplate jdbc, String database) {
        String marker = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                INSERT INTO integration_events (
                    event_id, producer, event_type, tenant_id, aggregate_type, aggregate_id, occurred_at, payload_json
                ) VALUES (?, 'fixture-reset', 'fixture.reset', 1, 'fixture', 1, UTC_TIMESTAMP(), JSON_OBJECT())
                """, database + "-" + marker);
        jdbc.update("""
                INSERT INTO redirect_cache_invalidation_outbox (
                    tenant_id, domain_id, domain_scope, code, status, generation, next_attempt_at
                ) VALUES (1, NULL, 0, ?, 'PENDING', 1, UTC_TIMESTAMP())
                """, marker);

        Long eventSequence = jdbc.queryForObject(
                "SELECT seq FROM integration_events WHERE event_id = ?",
                Long.class,
                database + "-" + marker
        );
        Long outboxId = jdbc.queryForObject(
                "SELECT id FROM redirect_cache_invalidation_outbox WHERE code = ?",
                Long.class,
                marker
        );
        return new AutoIncrementIds(eventSequence, outboxId);
    }

    private static String redis(String... command) throws Exception {
        String[] args = new String[command.length + 1];
        args[0] = "redis-cli";
        System.arraycopy(command, 0, args, 1, command.length);
        Container.ExecResult result = SharedIntegrationTopology.redis().execInContainer(args);
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        return result.getStdout().trim();
    }

    private record AutoIncrementIds(long integrationEventSequence, long redirectInvalidationOutboxId) {
    }
}
