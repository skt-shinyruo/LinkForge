package com.linkforge.shortlink.application;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.eventing.IntegrationEventStore;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.redirect.application.RedirectService;
import com.linkforge.redirect.application.RedirectResolution;
import com.linkforge.redirect.application.ResolveRedirectRequest;
import com.linkforge.shortlink.application.csv.ShortLinkCsvImportRow;
import com.linkforge.testsupport.SharedIntegrationTestSupport;
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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.scheduling.enabled=false"
)
class ShortLinkCacheAfterCommitIntegrationTest extends SharedIntegrationTestSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        // 访问明细 + 维度聚合测试开关（避免调度影响测试稳定性）
    }

    @Autowired
    ShortLinkApplicationService shortLinkService;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    IntegrationEventStore integrationEventStore;

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
            CreateLinkRequest req = new CreateLinkRequest(
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
            LinkDto dto = shortLinkService.create(TENANT_ID, CreatedBy.user(USER_ID), req);

            // BEFORE_COMMIT: cache write is after-commit only.
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
        CreateLinkRequest createReq = new CreateLinkRequest(
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
        LinkDto created = shortLinkService.create(TENANT_ID, CreatedBy.user(USER_ID), createReq);
        String key = key(created.code());

        assertThat(resolve(created.code()).meta().originalUrl()).isEqualTo("https://example.com/old");
        String before = redis.opsForValue().get(key);
        assertThat(before).isNotNull();

        long maxSeqBefore = integrationEventStore.loadMaxSeq();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            UpdateLinkRequest updateReq = new UpdateLinkRequest(
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

            // BEFORE_COMMIT: cache eviction is after-commit only.
            assertThat(redis.opsForValue().get(key)).isEqualTo(before);

            status.setRollbackOnly();
        });

        // ROLLBACK: no event appended and cache remains unchanged.
        assertThat(integrationEventStore.loadMaxSeq()).isEqualTo(maxSeqBefore);
        assertThat(redis.opsForValue().get(key)).isEqualTo(before);
    }

    @Test
    void create_commit_shouldEvictNegativeCacheAndResolveWithoutProjectorDrain() {
        String code = uniqueCode("create");
        String key = key(code);

        assertLinkNotFound(code);
        assertThat(redis.opsForValue().get(key)).isNotNull();

        CreateLinkRequest req = new CreateLinkRequest(
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
        shortLinkService.create(TENANT_ID, CreatedBy.user(USER_ID), req);

        assertThat(redis.opsForValue().get(key)).isNull();
        assertThat(resolve(code).meta().originalUrl()).isEqualTo("https://example.com/create");
        assertThat(redis.opsForValue().get(key)).isNotNull();
    }

    @Test
    void draftLink_shouldNotPubliclyRedirectEvenWhenEnabled() {
        String code = uniqueCode("draft");
        CreateLinkRequest req = new CreateLinkRequest(
                "https://example.com/draft",
                "note",
                null,
                true,
                code,
                Set.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "DRAFT"
        );
        shortLinkService.create(TENANT_ID, CreatedBy.user(USER_ID), req);

        RedirectResolution resolution = redirectService.resolve(
                new ResolveRedirectRequest(code, null, false, false, null)
        );

        assertThat(resolution.kind()).isEqualTo(RedirectResolution.Kind.UNAVAILABLE);
        assertThat(resolution.unavailableReason()).isEqualTo(RedirectResolution.UnavailableReason.DISABLED);
    }

    @Test
    void update_commit_shouldEvictPositiveCacheAndResolveUpdatedLinkWithoutProjectorDrain() {
        CreateLinkRequest createReq = new CreateLinkRequest(
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
        LinkDto created = shortLinkService.create(TENANT_ID, CreatedBy.user(USER_ID), createReq);
        String code = created.code();
        String key = key(code);

        assertThat(resolve(code).meta().originalUrl()).isEqualTo("https://example.com/old");
        String before = redis.opsForValue().get(key);
        assertThat(before).isNotNull();

        UpdateLinkRequest updateReq = new UpdateLinkRequest(
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
        assertThat(resolve(code).meta().originalUrl()).isEqualTo("https://example.com/new");
        String after = redis.opsForValue().get(key);
        assertThat(after).isNotNull();
        assertThat(after).contains("https://example.com/new");
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    void archive_restore_delete_commits_shouldInvalidateCacheWithoutProjectorDrain() {
        CreateLinkRequest createReq = new CreateLinkRequest(
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
        LinkDto created = shortLinkService.create(TENANT_ID, CreatedBy.user(USER_ID), createReq);
        String code = created.code();
        String key = key(code);

        assertThat(resolve(code).code()).isEqualTo(code);
        assertThat(redis.opsForValue().get(key)).isNotNull();

        shortLinkService.archive(TENANT_ID, created.id());
        assertThat(redis.opsForValue().get(key)).isNull();

        assertLinkNotFound(code);
        assertThat(redis.opsForValue().get(key)).isNotNull();

        shortLinkService.restore(TENANT_ID, created.id());
        assertThat(redis.opsForValue().get(key)).isNull();

        assertThat(resolve(code).code()).isEqualTo(code);
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

            ImportResult r = shortLinkService.importCsv(
                    TENANT_ID,
                    CreatedBy.user(USER_ID),
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
        assertThat(resolve(code).kind()).isEqualTo(RedirectResolution.Kind.NOT_FOUND);
    }

    private RedirectResolution resolve(String code) {
        return redirectService.resolve(new ResolveRedirectRequest(code, null, false, false, null));
    }

    private static String uniqueCode(String prefix) {
        return prefix + Long.toHexString(System.nanoTime());
    }
}
