package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.platform.ApplicationQuotaView;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.shortlink.application.*;
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.ApplicationLinkQuotaReservationPort;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.RedirectCacheInvalidationOutboxPort;
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.domain.ShortLink;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreateShortLinkCommandHandlerTest {

    @Test
    void constructor_shouldDependOnApplicationScopePort_insteadOfPlatformControlPlaneService() {
        Constructor<?> constructor = CreateShortLinkCommandHandler.class.getDeclaredConstructors()[0];

        assertThat(constructor.getParameterTypes())
                .contains(ApplicationScopePort.class);
        assertThat(constructor.getParameterTypes())
                .extracting(Class::getName)
                .doesNotContain("com.linkforge.platform.application.PlatformControlPlaneService");
    }

    @Test
    void constructor_shouldDependOnDomainEventDispatcherInsteadOfEventPublisher() {
        Constructor<?> constructor = CreateShortLinkCommandHandler.class.getDeclaredConstructors()[0];

        assertThat(constructor.getParameterTypes())
                .contains(ShortLinkDomainEventDispatcher.class);
        assertThat(constructor.getParameterTypes())
                .doesNotContain(ShortLinkEventPublisher.class);
    }

    @Test
    void handle_shouldValidateApplicationScopeAndQuota_viaPlatformContract() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(101L);

        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        SetLinkTagsCommandHandler setLinkTagsHandler = mock(SetLinkTagsCommandHandler.class);
        LinkTagRepository linkTagRepository = mock(LinkTagRepository.class);
        ShortLinkDomainEventDispatcher domainEventDispatcher = mock(ShortLinkDomainEventDispatcher.class);
        RedirectCacheSyncPort redirectCacheSync = mock(RedirectCacheSyncPort.class);
        RedirectCacheInvalidationOutboxPort redirectCacheInvalidationOutbox = mock(RedirectCacheInvalidationOutboxPort.class);
        ShortLinkDtoMapper dtoMapper = mock(ShortLinkDtoMapper.class);
        PostCommitHookPort postCommitHookPort = mock(PostCommitHookPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        ApplicationLinkQuotaReservationPort quotaReservationPort = mock(ApplicationLinkQuotaReservationPort.class);

        CreateShortLinkCommandHandler handler = new CreateShortLinkCommandHandler(
                idGenerator,
                shortLinkRepository,
                quotaReservationPort,
                setLinkTagsHandler,
                linkTagRepository,
                domainEventDispatcher,
                redirectCacheSync,
                redirectCacheInvalidationOutbox,
                dtoMapper,
                postCommitHookPort,
                clock,
                applicationScopePort
        );

        when(applicationScopePort.findApplicationQuota(1L, 2001L))
                .thenReturn(Optional.of(new ApplicationQuotaView(2001L, 10L, 100L)));
        when(quotaReservationPort.tryReserveMonthlyLink(
                1L,
                2001L,
                LocalDate.parse("2026-04-01"),
                LocalDateTime.parse("2026-04-01T00:00:00"),
                LocalDateTime.parse("2026-05-01T00:00:00"),
                10L
        )).thenReturn(true);
        doAnswer(invocation -> {
            ShortLink inserted = invocation.getArgument(0);
            when(shortLinkRepository.findByTenantIdAndId(1L, inserted.id())).thenReturn(Optional.of(inserted));
            return null;
        }).when(shortLinkRepository).insert(any(ShortLink.class));
        when(linkTagRepository.findTagNamesByLinkId(101L)).thenReturn(List.of("alpha"));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(postCommitHookPort).run(any(Runnable.class));
        doThrow(new IllegalStateException("redis unavailable"))
                .when(redirectCacheSync).evict(1L, 3001L, "abc123");

        LinkDto expected = new LinkDto(
                101L,
                1L,
                2001L,
                3001L,
                "DRAFT",
                "abc123",
                "https://lf/r/abc123",
                "https://example.com",
                "note",
                true,
                null,
                null,
                302,
                false,
                null,
                null,
                List.of(),
                List.of("alpha"),
                clock.instant()
        );
        when(dtoMapper.toDto(any(ShortLink.class), eq(List.of("alpha")))).thenReturn(expected);

        LinkDto actual = handler.handle(
                1L,
                CreatedBy.user(99L),
                new CreateLinkRequest(
                        "https://example.com",
                        "note",
                        null,
                        true,
                        "abc123",
                        Set.of("alpha"),
                        302,
                        false,
                        null,
                        null,
                        List.of(),
                        2001L,
                        3001L,
                        "DRAFT"
                )
        );

        assertThat(actual).isSameAs(expected);
        verify(applicationScopePort).requireApplicationAndDomainAuthorized(1L, 2001L, 3001L);
        verify(applicationScopePort).findApplicationQuota(1L, 2001L);
        verify(quotaReservationPort).tryReserveMonthlyLink(
                1L,
                2001L,
                LocalDate.parse("2026-04-01"),
                LocalDateTime.parse("2026-04-01T00:00:00"),
                LocalDateTime.parse("2026-05-01T00:00:00"),
                10L
        );
        verify(domainEventDispatcher).publish(any(ShortLink.class), eq(clock.instant()));
        verify(redirectCacheInvalidationOutbox).enqueue(1L, 3001L, "abc123");
        verify(postCommitHookPort).run(any(Runnable.class));
    }

    @Test
    void handle_shouldRejectWhenMonthlyLinkQuotaReachedWithinCurrentUtcMonth() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        ApplicationLinkQuotaReservationPort quotaReservationPort = mock(ApplicationLinkQuotaReservationPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-30T23:59:59Z"), ZoneOffset.UTC);
        CreateShortLinkCommandHandler handler = new CreateShortLinkCommandHandler(
                idGenerator,
                shortLinkRepository,
                quotaReservationPort,
                mock(SetLinkTagsCommandHandler.class),
                mock(LinkTagRepository.class),
                mock(ShortLinkDomainEventDispatcher.class),
                mock(RedirectCacheSyncPort.class),
                mock(RedirectCacheInvalidationOutboxPort.class),
                mock(ShortLinkDtoMapper.class),
                mock(PostCommitHookPort.class),
                clock,
                applicationScopePort
        );

        when(applicationScopePort.findApplicationQuota(1L, 2001L))
                .thenReturn(Optional.of(new ApplicationQuotaView(2001L, 2L, 100L)));
        when(quotaReservationPort.tryReserveMonthlyLink(
                1L,
                2001L,
                LocalDate.parse("2026-04-01"),
                LocalDateTime.parse("2026-04-01T00:00:00"),
                LocalDateTime.parse("2026-05-01T00:00:00"),
                2L
        )).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(
                1L,
                CreatedBy.user(99L),
                new CreateLinkRequest(
                        "https://example.com",
                        "note",
                        null,
                        true,
                        "abc123",
                        Set.of("alpha"),
                        302,
                        false,
                        null,
                        null,
                        List.of(),
                        2001L,
                        3001L,
                        "ACTIVE"
                )
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(applicationScopePort).requireApplicationAndDomainAuthorized(1L, 2001L, 3001L);
        verifyNoInteractions(idGenerator);
    }

    @Test
    void handle_shouldNotOversellMonthlyLinkQuotaWhenConcurrentCreatesRace() throws Exception {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(101L, 102L);
        InMemoryShortLinkRepository shortLinkRepository = new InMemoryShortLinkRepository();
        LinkTagRepository linkTagRepository = mock(LinkTagRepository.class);
        when(linkTagRepository.findTagNamesByLinkId(101L)).thenReturn(List.of());
        when(linkTagRepository.findTagNamesByLinkId(102L)).thenReturn(List.of());
        ShortLinkDtoMapper dtoMapper = mock(ShortLinkDtoMapper.class);
        when(dtoMapper.toDto(any(ShortLink.class), eq(List.of()))).thenAnswer(invocation -> {
            ShortLink link = invocation.getArgument(0);
            return new LinkDto(
                    link.id(),
                    link.tenantId(),
                    link.applicationId(),
                    link.domainId(),
                    link.lifecycleState().name(),
                    link.code().value(),
                    "https://lf/r/" + link.code().value(),
                    link.originalUrl().value(),
                    link.note(),
                    link.enabled(),
                    null,
                    null,
                    link.redirectStatusCode(),
                    link.previewEnabled(),
                    null,
                    null,
                    List.of(),
                    List.of(),
                    Instant.parse("2026-04-01T00:00:00Z")
            );
        });
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        when(applicationScopePort.findApplicationQuota(1L, 2001L))
                .thenReturn(Optional.of(new ApplicationQuotaView(2001L, 1L, 100L)));
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        CreateShortLinkCommandHandler handler = new CreateShortLinkCommandHandler(
                idGenerator,
                shortLinkRepository,
                new SinglePermitQuotaReservationPort(),
                mock(SetLinkTagsCommandHandler.class),
                linkTagRepository,
                mock(ShortLinkDomainEventDispatcher.class),
                mock(RedirectCacheSyncPort.class),
                mock(RedirectCacheInvalidationOutboxPort.class),
                dtoMapper,
                mock(PostCommitHookPort.class),
                clock,
                applicationScopePort
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = executor.invokeAll(List.of(
                    createCall(handler, "raceA1"),
                    createCall(handler, "raceA2")
            ));

            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(5, TimeUnit.SECONDS));
            }

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(shortLinkRepository.insertedCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void handle_shouldRejectInvalidLifecycleStateAsBadRequestBeforeCreatingLink() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        CreateShortLinkCommandHandler handler = new CreateShortLinkCommandHandler(
                idGenerator,
                shortLinkRepository,
                mock(ApplicationLinkQuotaReservationPort.class),
                mock(SetLinkTagsCommandHandler.class),
                mock(LinkTagRepository.class),
                mock(ShortLinkDomainEventDispatcher.class),
                mock(RedirectCacheSyncPort.class),
                mock(RedirectCacheInvalidationOutboxPort.class),
                mock(ShortLinkDtoMapper.class),
                mock(PostCommitHookPort.class),
                Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC),
                mock(ApplicationScopePort.class)
        );

        assertThatThrownBy(() -> handler.handle(
                1L,
                CreatedBy.user(99L),
                new CreateLinkRequest(
                        "https://example.com",
                        "note",
                        null,
                        true,
                        "abc123",
                        Set.of("alpha"),
                        302,
                        false,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        "NOT_A_STATE"
                )
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verifyNoInteractions(idGenerator, shortLinkRepository);
    }

    private static Callable<Boolean> createCall(CreateShortLinkCommandHandler handler, String code) {
        return () -> {
            try {
                handler.handle(
                        1L,
                        CreatedBy.user(99L),
                        new CreateLinkRequest(
                                "https://example.com/" + code,
                                "note",
                                null,
                                true,
                                code,
                                Set.of(),
                                302,
                                false,
                                null,
                                null,
                                List.of(),
                                2001L,
                                3001L,
                                "ACTIVE"
                        )
                );
                return true;
            } catch (BusinessException ex) {
                if (ex.getErrorCode() == ErrorCode.FORBIDDEN) {
                    return false;
                }
                throw ex;
            }
        };
    }

    private static final class SinglePermitQuotaReservationPort implements ApplicationLinkQuotaReservationPort {
        private final AtomicLong used = new AtomicLong();

        @Override
        public boolean tryReserveMonthlyLink(
                long tenantId,
                long applicationId,
                LocalDate monthStartUtc,
                LocalDateTime fromInclusiveUtc,
                LocalDateTime toExclusiveUtc,
                long monthlyLinkLimit
        ) {
            while (true) {
                long current = used.get();
                if (current >= monthlyLinkLimit) {
                    return false;
                }
                if (used.compareAndSet(current, current + 1)) {
                    return true;
                }
            }
        }
    }

    private static final class InMemoryShortLinkRepository implements ShortLinkRepository {
        private final List<ShortLink> inserted = Collections.synchronizedList(new ArrayList<>());
        private final ConcurrentHashMap<Long, ShortLink> linksById = new ConcurrentHashMap<>();

        @Override
        public Optional<ShortLink> findByTenantIdAndId(long tenantId, long linkId) {
            return Optional.ofNullable(linksById.get(linkId))
                    .filter(link -> link.tenantId() == tenantId);
        }

        @Override
        public Optional<ShortLink> findUnscopedByCode(String code) {
            return inserted.stream()
                    .filter(link -> link.domainId() == null)
                    .filter(link -> link.code().value().equals(code))
                    .findFirst();
        }

        @Override
        public Optional<ShortLink> findByDomainIdAndCode(long domainId, String code) {
            return inserted.stream()
                    .filter(link -> Long.valueOf(domainId).equals(link.domainId()))
                    .filter(link -> link.code().value().equals(code))
                    .findFirst();
        }

        @Override
        public long countCreatedByTenantIdAndApplicationIdAndCreatedAtRange(
                long tenantId,
                long applicationId,
                LocalDateTime fromInclusiveUtc,
                LocalDateTime toExclusiveUtc
        ) {
            return inserted.stream()
                    .filter(link -> link.tenantId() == tenantId)
                    .filter(link -> Long.valueOf(applicationId).equals(link.applicationId()))
                    .count();
        }

        @Override
        public void insert(ShortLink link) {
            inserted.add(link);
            linksById.put(link.id(), link);
        }

        @Override
        public boolean update(ShortLink link) {
            return false;
        }

        @Override
        public boolean delete(ShortLink link) {
            return false;
        }

        @Override
        public long countSearch(long tenantId, com.linkforge.shortlink.application.query.ShortLinkSearchQuery query) {
            return 0;
        }

        @Override
        public List<ShortLink> listSearch(
                long tenantId,
                com.linkforge.shortlink.application.query.ShortLinkSearchQuery query,
                long offset,
                int limit
        ) {
            return List.of();
        }

        int insertedCount() {
            return inserted.size();
        }
    }
}
