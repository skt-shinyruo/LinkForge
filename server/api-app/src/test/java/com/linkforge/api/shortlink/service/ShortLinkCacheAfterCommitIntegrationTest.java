package com.linkforge.api.shortlink.service;

import com.linkforge.api.LinkForgeApiApplication;
import com.linkforge.api.security.AuthPrincipal;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = LinkForgeApiApplication.class,
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
            .withExposedPorts(6379);

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
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    LinkCacheOutboxJob linkCacheOutboxJob;

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 1L;

    @BeforeEach
    void setUpAuth() {
        AuthPrincipal principal = new AuthPrincipal(USER_ID, TENANT_ID, "admin@example.com", Set.of("tenant_admin"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "N/A", List.of())
        );

        // keep tests isolated
        jdbcTemplate.update("DELETE FROM link_cache_outbox");
    }

    @AfterEach
    void tearDownAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_rollback_shouldNotWriteRedisCache() {
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
                    null
            );
            ShortLinkService.LinkDto dto = shortLinkService.create(TENANT_ID, USER_ID, req);

            // BEFORE_COMMIT: cache write should not be visible yet
            assertThat(redis.opsForValue().get(key(dto.code()))).isNull();

            status.setRollbackOnly();
            return dto.code();
        });

        // ROLLBACK: cache write should never happen
        assertThat(redis.opsForValue().get(key(code))).isNull();
        Integer outboxRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM link_cache_outbox WHERE code = ?",
                Integer.class,
                code
        );
        assertThat(outboxRows).isEqualTo(0);
    }

    @Test
    void update_rollback_shouldNotEvictOrOverwriteRedisCache() {
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
                null
        );
        ShortLinkService.LinkDto created = shortLinkService.create(TENANT_ID, USER_ID, createReq);
        String key = key(created.code());

        String before = redis.opsForValue().get(key);
        assertThat(before).isNotNull();

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
                    null
            );
            shortLinkService.update(TENANT_ID, created.id(), updateReq);

            // BEFORE_COMMIT: cache should remain unchanged
            assertThat(redis.opsForValue().get(key)).isEqualTo(before);

            status.setRollbackOnly();
        });

        // ROLLBACK: cache should still remain unchanged
        assertThat(redis.opsForValue().get(key)).isEqualTo(before);
    }

    @Test
    void archive_commit_shouldEvictRedisCacheAfterCommit() {
        ShortLinkService.CreateLinkRequest createReq = new ShortLinkService.CreateLinkRequest(
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
                null
        );
        ShortLinkService.LinkDto created = shortLinkService.create(TENANT_ID, USER_ID, createReq);
        String key = key(created.code());

        assertThat(redis.opsForValue().get(key)).isNotNull();

        shortLinkService.archive(TENANT_ID, created.id());

        assertThat(redis.opsForValue().get(key)).isNull();
    }

    @Test
    void create_commit_outboxJob_shouldRefreshCacheWhenMissing() {
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
                null
        );
        ShortLinkService.LinkDto created = shortLinkService.create(TENANT_ID, USER_ID, req);
        String code = created.code();
        String key = key(code);

        // outbox should be persisted in DB transaction
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM link_cache_outbox WHERE code = ?",
                String.class,
                code
        );
        assertThat(status).isEqualTo("PENDING");

        // simulate: commit happened but cache update was lost (crash before afterCommit)
        redis.delete(key);
        assertThat(redis.opsForValue().get(key)).isNull();

        linkCacheOutboxJob.drain();

        assertThat(redis.opsForValue().get(key)).isNotNull();
        String statusAfter = jdbcTemplate.queryForObject(
                "SELECT status FROM link_cache_outbox WHERE code = ?",
                String.class,
                code
        );
        assertThat(statusAfter).isEqualTo("DONE");
    }

    @Test
    void archive_commit_outboxJob_shouldEvictStaleCache() {
        ShortLinkService.CreateLinkRequest createReq = new ShortLinkService.CreateLinkRequest(
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
                null
        );
        ShortLinkService.LinkDto created = shortLinkService.create(TENANT_ID, USER_ID, createReq);
        String code = created.code();
        String key = key(code);

        String raw = redis.opsForValue().get(key);
        assertThat(raw).isNotNull();

        shortLinkService.archive(TENANT_ID, created.id());
        assertThat(redis.opsForValue().get(key)).isNull();

        // simulate: stale cache was (wrongly) written back after archive
        redis.opsForValue().set(key, raw);
        assertThat(redis.opsForValue().get(key)).isNotNull();

        linkCacheOutboxJob.drain();

        assertThat(redis.opsForValue().get(key)).isNull();
    }

    private static String key(String code) {
        return "link:code:" + code;
    }
}
