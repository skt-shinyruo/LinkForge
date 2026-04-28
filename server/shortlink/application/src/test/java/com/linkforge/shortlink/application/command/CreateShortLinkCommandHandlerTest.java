package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.platform.ApplicationQuotaView;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.shortlink.application.ShortLinkService;
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.domain.ShortLink;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
        ShortLinkDtoMapper dtoMapper = mock(ShortLinkDtoMapper.class);
        PostCommitHookPort postCommitHookPort = mock(PostCommitHookPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);

        CreateShortLinkCommandHandler handler = new CreateShortLinkCommandHandler(
                idGenerator,
                shortLinkRepository,
                setLinkTagsHandler,
                linkTagRepository,
                domainEventDispatcher,
                redirectCacheSync,
                dtoMapper,
                postCommitHookPort,
                clock,
                applicationScopePort
        );

        when(applicationScopePort.findApplicationQuota(1L, 2001L))
                .thenReturn(Optional.of(new ApplicationQuotaView(2001L, 10L, 100L)));
        when(shortLinkRepository.countCreatedByTenantIdAndApplicationIdAndCreatedAtRange(
                1L,
                2001L,
                LocalDateTime.parse("2026-04-01T00:00:00"),
                LocalDateTime.parse("2026-05-01T00:00:00")
        )).thenReturn(2L);
        doAnswer(invocation -> {
            ShortLink inserted = invocation.getArgument(0);
            when(shortLinkRepository.findByTenantIdAndId(1L, inserted.id())).thenReturn(Optional.of(inserted));
            return null;
        }).when(shortLinkRepository).insert(any(ShortLink.class));
        when(linkTagRepository.findTagNamesByLinkId(101L)).thenReturn(List.of("alpha"));

        ShortLinkService.LinkDto expected = new ShortLinkService.LinkDto(
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

        ShortLinkService.LinkDto actual = handler.handle(
                1L,
                ShortLinkService.CreatedBy.user(99L),
                new ShortLinkService.CreateLinkRequest(
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
        verify(shortLinkRepository).countCreatedByTenantIdAndApplicationIdAndCreatedAtRange(
                1L,
                2001L,
                LocalDateTime.parse("2026-04-01T00:00:00"),
                LocalDateTime.parse("2026-05-01T00:00:00")
        );
        verify(domainEventDispatcher).publish(any(ShortLink.class), eq(clock.instant()));
    }

    @Test
    void handle_shouldRejectWhenMonthlyLinkQuotaReachedWithinCurrentUtcMonth() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-30T23:59:59Z"), ZoneOffset.UTC);
        CreateShortLinkCommandHandler handler = new CreateShortLinkCommandHandler(
                idGenerator,
                shortLinkRepository,
                mock(SetLinkTagsCommandHandler.class),
                mock(LinkTagRepository.class),
                mock(ShortLinkDomainEventDispatcher.class),
                mock(RedirectCacheSyncPort.class),
                mock(ShortLinkDtoMapper.class),
                mock(PostCommitHookPort.class),
                clock,
                applicationScopePort
        );

        when(applicationScopePort.findApplicationQuota(1L, 2001L))
                .thenReturn(Optional.of(new ApplicationQuotaView(2001L, 2L, 100L)));
        when(shortLinkRepository.countCreatedByTenantIdAndApplicationIdAndCreatedAtRange(
                1L,
                2001L,
                LocalDateTime.parse("2026-04-01T00:00:00"),
                LocalDateTime.parse("2026-05-01T00:00:00")
        )).thenReturn(2L);

        assertThatThrownBy(() -> handler.handle(
                1L,
                ShortLinkService.CreatedBy.user(99L),
                new ShortLinkService.CreateLinkRequest(
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
    void handle_shouldRejectInvalidLifecycleStateAsBadRequestBeforeCreatingLink() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        CreateShortLinkCommandHandler handler = new CreateShortLinkCommandHandler(
                idGenerator,
                shortLinkRepository,
                mock(SetLinkTagsCommandHandler.class),
                mock(LinkTagRepository.class),
                mock(ShortLinkDomainEventDispatcher.class),
                mock(RedirectCacheSyncPort.class),
                mock(ShortLinkDtoMapper.class),
                mock(PostCommitHookPort.class),
                Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC),
                mock(ApplicationScopePort.class)
        );

        assertThatThrownBy(() -> handler.handle(
                1L,
                ShortLinkService.CreatedBy.user(99L),
                new ShortLinkService.CreateLinkRequest(
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
}
