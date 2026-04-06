package com.linkforge.shortlink.application;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.eventing.IntegrationEventStore;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.redirect.application.RedirectService;
import com.linkforge.redirect.application.error.RedirectBusinessException;
import com.linkforge.redirect.application.error.RedirectErrorCode;
import com.linkforge.redirect.infrastructure.projection.ShortLinkEventProjectorJob;
import com.linkforge.shortlink.application.csv.ShortLinkCsvImportRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.scheduling.enabled=false"
)
class ShortLinkCacheAfterCommitIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("linkforge")
            .withUsername("linkforge")
            .withPassword("linkforge");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2.4-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1)
                    .withStartupTimeout(Duration.ofSeconds(120)))
            .withStartupAttempts(3);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);

        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // 测试环境固定密钥，避免启动失败
        r.add("app.security.jwt.secret", () -> "test-secret-please-change-but-long-enough-32-bytes");
        r.add("app.analytics.salt", () -> "test-analytics-salt");

        // 访问明细 + 维度聚合测试开关（避免调度影响测试稳定性）
        r.add("app.analytics.dimensions.enabled", () -> "false");
        r.add("app.analytics.events.enabled", () -> "false");
        r.add("app.analytics.events.sample-rate", () -> "1");
    }

    @Autowired
    ShortLinkService shortLinkService;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    IntegrationEventStore integrationEventStore;

    @Autowired
    ShortLinkEventProjectorJob redirectProjector;

    @Autowired
    RedirectService redirectService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 1L;

    @BeforeEach
    void setUpAuth() {
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, TENANT_ID);
        AuthPrincipal principal = new AuthPrincipal(USER_ID, TENANT_ID, "admin@example.com", Set.of("tenant_admin"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "N/A", List.of())
        );
    }

    @AfterEach
    void tearDownAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_rollback_shouldNotAppendEventOrWriteRedisCache() {
        long maxSeqBefore = integrationEventStore.loadMaxSeq();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        String code = tx.execute(status -> {
            ShortLinkService.CreateLinkRequest req = new ShortLinkService.CreateLinkRequest(
                    "https://example.com",
                    "note",
                    null,
                    null,
                    null,
                    Set.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
            ShortLinkService.LinkDto dto = shortLinkService.create(TENANT_ID, ShortLinkService.CreatedBy.user(USER_ID), req);

            // BEFORE_COMMIT: nothing should be visible yet (event + projection + cache are transactional / async)
            assertThat(redis.opsForValue().get(key(dto.code()))).isNull();

            status.setRollbackOnly();
            return dto.code();
        });

        // ROLLBACK: no event appended and no cache written
        assertThat(redis.opsForValue().get(key(code))).isNull();
        assertThat(integrationEventStore.loadMaxSeq()).isEqualTo(maxSeqBefore);
    }

    @Test
    void update_rollback_shouldNotAppendEventOrChangeRedisCache() {
        ShortLinkService.CreateLinkRequest createReq = new ShortLinkService.CreateLinkRequest(
                "https://example.com/old",
                "note",
                null,
                null,
                null,
                Set.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        ShortLinkService.LinkDto created = shortLinkService.create(TENANT_ID, ShortLinkService.CreatedBy.user(USER_ID), createReq);
        String key = key(created.code());

        redirectProjector.drain();
        assertThat(redirectService.resolve(created.code()).originalUrl()).isEqualTo("https://example.com/old");
        String before = redis.opsForValue().get(key);
        assertThat(before).isNotNull();

        long maxSeqBefore = integrationEventStore.loadMaxSeq();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShortLinkService.UpdateLinkRequest updateReq = new ShortLinkService.UpdateLinkRequest(
                    "https://example.com/new",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
            shortLinkService.update(TENANT_ID, created.id(), updateReq, currentActor(), LocalDateTime.now(ZoneOffset.UTC));

            // BEFORE_COMMIT: projector not run, cache should remain unchanged
            assertThat(redis.opsForValue().get(key)).isEqualTo(before);

            status.setRollbackOnly();
        });

        // ROLLBACK: no event appended, projector drains nothing, cache still unchanged
        assertThat(integrationEventStore.loadMaxSeq()).isEqualTo(maxSeqBefore);
        redirectProjector.drain();
        assertThat(redis.opsForValue().get(key)).isEqualTo(before);
    }

    @Test
    void create_commit_shouldEvictNegativeCacheAndResolveWithoutProjectorDrain() {
        String code = uniqueCode("create");
        String key = key(code);

        assertLinkNotFound(code);
        assertThat(redis.opsForValue().get(key)).isNotNull();

        ShortLinkService.CreateLinkRequest req = new ShortLinkService.CreateLinkRequest(
                "https://example.com/create",
                "note",
                null,
                null,
                code,
                Set.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        shortLinkService.create(TENANT_ID, ShortLinkService.CreatedBy.user(USER_ID), req);

        assertThat(redis.opsForValue().get(key)).isNull();
        assertThat(redirectService.resolve(code).originalUrl()).isEqualTo("https://example.com/create");
        assertThat(redis.opsForValue().get(key)).isNotNull();
    }

    @Test
    void update_commit_shouldEvictPositiveCacheAndResolveUpdatedLinkWithoutProjectorDrain() {
        ShortLinkService.CreateLinkRequest createReq = new ShortLinkService.CreateLinkRequest(
                "https://example.com/old",
                "note",
                null,
                null,
                null,
                Set.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        ShortLinkService.LinkDto created = shortLinkService.create(TENANT_ID, ShortLinkService.CreatedBy.user(USER_ID), createReq);
        String code = created.code();
        String key = key(code);

        assertThat(redirectService.resolve(code).originalUrl()).isEqualTo("https://example.com/old");
        assertThat(redis.opsForValue().get(key)).isNotNull();

        ShortLinkService.UpdateLinkRequest updateReq = new ShortLinkService.UpdateLinkRequest(
                "https://example.com/new",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        shortLinkService.update(TENANT_ID, created.id(), updateReq, currentActor(), LocalDateTime.now(ZoneOffset.UTC));

        assertThat(redis.opsForValue().get(key)).isNull();
        assertThat(redirectService.resolve(code).originalUrl()).isEqualTo("https://example.com/new");
        assertThat(redis.opsForValue().get(key)).isNotNull();
    }

    @Test
    void archive_restore_delete_commits_shouldInvalidateCacheWithoutProjectorDrain() {
        ShortLinkService.CreateLinkRequest createReq = new ShortLinkService.CreateLinkRequest(
                "https://example.com/live",
                "note",
                null,
                null,
                null,
                Set.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        ShortLinkService.LinkDto created = shortLinkService.create(TENANT_ID, ShortLinkService.CreatedBy.user(USER_ID), createReq);
        String code = created.code();
        String key = key(code);

        assertThat(redirectService.resolve(code).code()).isEqualTo(code);
        assertThat(redis.opsForValue().get(key)).isNotNull();

        shortLinkService.archive(TENANT_ID, created.id());
        assertThat(redis.opsForValue().get(key)).isNull();

        assertLinkNotFound(code);
        assertThat(redis.opsForValue().get(key)).isNotNull();

        shortLinkService.restore(TENANT_ID, created.id());
        assertThat(redis.opsForValue().get(key)).isNull();

        assertThat(redirectService.resolve(code).code()).isEqualTo(code);
        assertThat(redis.opsForValue().get(key)).isNotNull();

        shortLinkService.archive(TENANT_ID, created.id());
        assertThat(redis.opsForValue().get(key)).isNull();

        assertLinkNotFound(code);
        assertThat(redis.opsForValue().get(key)).isNotNull();

        shortLinkService.delete(TENANT_ID, created.id());
        assertThat(redis.opsForValue().get(key)).isNull();

        assertLinkNotFound(code);
    }

    @Test
    void importCsv_inExistingTx_shouldNotAccumulateAfterCommitSynchronizations() {
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        outer.executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
            int before = TransactionSynchronizationManager.getSynchronizations().size();

            ShortLinkService.ImportResult r = shortLinkService.importCsv(
                    TENANT_ID,
                    ShortLinkService.CreatedBy.user(USER_ID),
                    List.of(
                            new ShortLinkCsvImportRow(1L, "https://example.com/a", null, null, null, null),
                            new ShortLinkCsvImportRow(2L, "https://example.com/b", null, null, null, null)
                    )
            );
            assertThat(r.success()).isEqualTo(2);
            assertThat(r.failed()).isEqualTo(0);

            int after = TransactionSynchronizationManager.getSynchronizations().size();
            assertThat(after - before).isEqualTo(0);

            status.setRollbackOnly();
        });
    }

    private static String key(String code) {
        return "link:code:" + code;
    }

    private static UserActor currentActor() {
        return new UserActor(TENANT_ID, USER_ID, "admin@example.com", Set.of("tenant_admin"));
    }

    private void assertLinkNotFound(String code) {
        assertThatThrownBy(() -> redirectService.resolve(code))
                .isInstanceOf(RedirectBusinessException.class)
                .extracting(e -> ((RedirectBusinessException) e).getErrorCode())
                .isEqualTo(RedirectErrorCode.LINK_NOT_FOUND);
    }

    private static String uniqueCode(String prefix) {
        return prefix + Long.toHexString(System.nanoTime());
    }
}
