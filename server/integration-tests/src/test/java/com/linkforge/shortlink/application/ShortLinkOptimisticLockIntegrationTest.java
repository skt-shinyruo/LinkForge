package com.linkforge.shortlink.application;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.shortlink.infrastructure.persistence.repository.MybatisShortLinkRepository;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkQueryMapper;
import com.linkforge.testsupport.SharedIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.scheduling.enabled=false"
)
class ShortLinkOptimisticLockIntegrationTest extends SharedIntegrationTestSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
    }

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 1L;

    @Autowired
    ShortLinkApplicationService shortLinkService;

    @Autowired
    ShortLinkQueryMapper shortLinkQueryMapper;

    @SpyBean
    MybatisShortLinkRepository shortLinkRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpAuth() {
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, TENANT_ID);
        setCurrentUser();
        reset(shortLinkRepository);
    }

    @AfterEach
    void tearDownAuth() {
        SecurityContextHolder.clearContext();
        reset(shortLinkRepository);
    }

    @Test
    void stale_update_should_fail_instead_of_overwriting_newer_state() throws Exception {
        LinkDto created = createLink("update-start");
        WriteGate gate = new WriteGate();
        doAnswer(gate.aroundRealMethod()).when(shortLinkRepository).update(any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<LinkDto> first = submitWithAuth(executor, () -> shortLinkService.update(
                    TENANT_ID,
                    created.id(),
                    updateRequest("https://example.com/update-first"),
                    currentActor(),
                    LocalDateTime.now(ZoneOffset.UTC)
            ));

            gate.awaitFirstEntry();

            Future<LinkDto> second = submitWithAuth(executor, () -> shortLinkService.update(
                    TENANT_ID,
                    created.id(),
                    updateRequest("https://example.com/update-second"),
                    currentActor(),
                    LocalDateTime.now(ZoneOffset.UTC)
            ));

            gate.awaitSecondEntry();
            gate.releaseFirst();

            LinkDto firstResult = first.get(10, TimeUnit.SECONDS);
            gate.releaseSecond();

            assertConflict(second);
            assertThat(firstResult.originalUrl()).isEqualTo("https://example.com/update-first");
            assertThat(shortLinkService.detail(TENANT_ID, created.id()).originalUrl())
                    .isEqualTo("https://example.com/update-first");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void stale_archive_should_fail_as_business_conflict() throws Exception {
        LinkDto created = createLink("archive-start");
        WriteGate gate = new WriteGate();
        doAnswer(gate.aroundRealMethod()).when(shortLinkRepository).update(any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<LinkDto> first = submitWithAuth(executor, () -> shortLinkService.archive(TENANT_ID, created.id()));
            gate.awaitFirstEntry();

            Future<LinkDto> second = submitWithAuth(executor, () -> shortLinkService.archive(TENANT_ID, created.id()));
            gate.awaitSecondEntry();

            gate.releaseFirst();
            LinkDto firstResult = first.get(10, TimeUnit.SECONDS);
            gate.releaseSecond();

            assertConflict(second);
            assertThat(firstResult.archivedAt()).isNotNull();
            assertThat(shortLinkService.detail(TENANT_ID, created.id()).archivedAt()).isNotNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void stale_restore_should_fail_as_business_conflict() throws Exception {
        LinkDto archived = archive(createLink("restore-start").id());
        WriteGate gate = new WriteGate();
        doAnswer(gate.aroundRealMethod()).when(shortLinkRepository).update(any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<LinkDto> first = submitWithAuth(executor, () -> shortLinkService.restore(TENANT_ID, archived.id()));
            gate.awaitFirstEntry();

            Future<LinkDto> second = submitWithAuth(executor, () -> shortLinkService.restore(TENANT_ID, archived.id()));
            gate.awaitSecondEntry();

            gate.releaseFirst();
            LinkDto firstResult = first.get(10, TimeUnit.SECONDS);
            gate.releaseSecond();

            assertConflict(second);
            assertThat(firstResult.archivedAt()).isNull();
            assertThat(shortLinkService.detail(TENANT_ID, archived.id()).archivedAt()).isNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void stale_delete_should_fail_as_business_conflict() throws Exception {
        LinkDto archived = archive(createLink("delete-start").id());
        WriteGate gate = new WriteGate();
        doAnswer(gate.aroundRealMethod()).when(shortLinkRepository).delete(any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> first = submitWithAuth(executor, () -> {
                shortLinkService.delete(TENANT_ID, archived.id());
                return null;
            });
            gate.awaitFirstEntry();

            Future<Void> second = submitWithAuth(executor, () -> {
                shortLinkService.delete(TENANT_ID, archived.id());
                return null;
            });
            gate.awaitSecondEntry();

            gate.releaseFirst();
            first.get(10, TimeUnit.SECONDS);
            gate.releaseSecond();

            assertConflict(second);
            assertThat(shortLinkQueryMapper.findByTenantIdAndId(TENANT_ID, archived.id())).isNull();
        } finally {
            executor.shutdownNow();
        }
    }

    private LinkDto createLink(String suffix) {
        return shortLinkService.create(
                TENANT_ID,
                CreatedBy.user(USER_ID),
                new CreateLinkRequest(
                        "https://example.com/" + suffix + "/" + Long.toUnsignedString(System.nanoTime()),
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
                )
        );
    }

    private LinkDto archive(long linkId) {
        return shortLinkService.archive(TENANT_ID, linkId);
    }

    private UpdateLinkRequest updateRequest(String originalUrl) {
        return new UpdateLinkRequest(
                originalUrl,
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
    }

    private <T> Future<T> submitWithAuth(ExecutorService executor, Callable<T> task) {
        return executor.submit(() -> {
            setCurrentUser();
            try {
                return task.call();
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }

    private static void setCurrentUser() {
        AuthPrincipal principal = new AuthPrincipal(USER_ID, TENANT_ID, "admin@example.com", Set.of("tenant_admin"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "N/A", List.of())
        );
    }

    private static UserActor currentActor() {
        return new UserActor(TENANT_ID, USER_ID, "admin@example.com", Set.of("tenant_admin"));
    }

    private static void assertConflict(Future<?> future) {
        assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(BusinessException.class);
    }

    private static final class WriteGate {
        private final AtomicInteger invocations = new AtomicInteger();
        private final CountDownLatch firstEntered = new CountDownLatch(1);
        private final CountDownLatch secondEntered = new CountDownLatch(1);
        private final CountDownLatch allowFirst = new CountDownLatch(1);
        private final CountDownLatch allowSecond = new CountDownLatch(1);

        Answer<Object> aroundRealMethod() {
            return invocation -> {
                int callNumber = invocations.incrementAndGet();
                if (callNumber == 1) {
                    firstEntered.countDown();
                    await(allowFirst);
                } else if (callNumber == 2) {
                    secondEntered.countDown();
                    await(allowSecond);
                }
                return invocation.callRealMethod();
            };
        }

        void awaitFirstEntry() {
            await(firstEntered);
        }

        void awaitSecondEntry() {
            await(secondEntered);
        }

        void releaseFirst() {
            allowFirst.countDown();
        }

        void releaseSecond() {
            allowSecond.countDown();
        }

        private static void await(CountDownLatch latch) {
            try {
                assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for write gate", ex);
            }
        }
    }
}
