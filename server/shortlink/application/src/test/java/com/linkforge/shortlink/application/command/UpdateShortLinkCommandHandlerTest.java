package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.governance.ApprovalRequester;
import com.linkforge.contract.governance.ApprovalSubmissionPort;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.shortlink.application.*;
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.RedirectCacheInvalidationOutboxPort;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.domain.CreatedByType;
import com.linkforge.shortlink.domain.HttpUrl;
import com.linkforge.shortlink.domain.ShortCode;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkLifecycleState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UpdateShortLinkCommandHandlerTest {

    @Test
    void handle_shouldReturnCurrentViewWithoutWritesForNormalizedNoOpPatch() {
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        SetLinkTagsCommandHandler setLinkTagsHandler = mock(SetLinkTagsCommandHandler.class);
        ShortLinkDomainEventDispatcher domainEventDispatcher = mock(ShortLinkDomainEventDispatcher.class);
        LinkTagRepository linkTagRepository = mock(LinkTagRepository.class);
        RedirectCacheSyncPort redirectCacheSync = mock(RedirectCacheSyncPort.class);
        RedirectCacheInvalidationOutboxPort outbox = mock(RedirectCacheInvalidationOutboxPort.class);
        ShortLinkDtoMapper dtoMapper = mock(ShortLinkDtoMapper.class);
        PostCommitHookPort postCommitHookPort = mock(PostCommitHookPort.class);
        ApprovalSubmissionPort approvalSubmissionPort = mock(ApprovalSubmissionPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        UpdateShortLinkCommandHandler handler = new UpdateShortLinkCommandHandler(
                shortLinkRepository,
                setLinkTagsHandler,
                domainEventDispatcher,
                linkTagRepository,
                redirectCacheSync,
                outbox,
                dtoMapper,
                postCommitHookPort,
                clock,
                approvalSubmissionPort
        );
        ShortLink link = ShortLink.create(
                100L,
                1L,
                null,
                null,
                ShortCode.of("sameLink"),
                ShortLinkLifecycleState.ACTIVE,
                HttpUrl.of("https://example.com/same"),
                "same-note",
                true,
                null,
                302,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                7L
        );
        LinkDto expected = new LinkDto(
                100L,
                1L,
                null,
                null,
                "ACTIVE",
                "sameLink",
                "https://lf/r/sameLink",
                "https://example.com/same",
                "same-note",
                true,
                null,
                null,
                302,
                false,
                null,
                null,
                List.of(),
                List.of("alpha"),
                Instant.parse("2026-03-31T00:00:00Z")
        );
        when(shortLinkRepository.findByTenantIdAndId(1L, 100L)).thenReturn(java.util.Optional.of(link));
        when(linkTagRepository.findTagNamesByLinkId(100L)).thenReturn(List.of("alpha"));
        when(dtoMapper.toDto(link, List.of("alpha"))).thenReturn(expected);

        LinkDto actual = handler.handle(
                1L,
                100L,
                new UpdateLinkRequest(
                        "https://example.com/same",
                        "same-note",
                        null,
                        null,
                        true,
                        Set.of(" alpha "),
                        302,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        "ACTIVE"
                ),
                new UserActor(1L, 7L, "owner@example.com", Set.of("USER")),
                LocalDateTime.parse("2026-04-01T00:00:00")
        );

        assertThat(actual).isSameAs(expected);
        verify(shortLinkRepository, never()).update(link);
        verifyNoInteractions(
                setLinkTagsHandler,
                domainEventDispatcher,
                redirectCacheSync,
                outbox,
                postCommitHookPort,
                approvalSubmissionPort
        );
    }

    @Test
    void constructor_shouldDependOnGovernanceApprovalSubmissionContract() {
        Constructor<?> constructor = UpdateShortLinkCommandHandler.class.getDeclaredConstructors()[0];

        assertThat(constructor.getParameterTypes())
                .contains(ApprovalSubmissionPort.class);
        assertThat(constructor.getParameterTypes())
                .extracting(Class::getName)
                .doesNotContain(
                        "com.linkforge.governance.application.GovernanceService",
                        "com.linkforge.governance.application.GovernanceApprovalRequestService"
                );
    }

    @Test
    void constructor_shouldDependOnDomainEventDispatcherInsteadOfEventPublisher() {
        Constructor<?> constructor = UpdateShortLinkCommandHandler.class.getDeclaredConstructors()[0];

        assertThat(constructor.getParameterTypes())
                .contains(ShortLinkDomainEventDispatcher.class);
        assertThat(constructor.getParameterTypes())
                .doesNotContain(ShortLinkEventPublisher.class);
    }

    @Test
    void handle_shouldRequestDestinationChangeApproval_viaNarrowGovernanceApi() {
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        SetLinkTagsCommandHandler setLinkTagsHandler = mock(SetLinkTagsCommandHandler.class);
        ShortLinkDomainEventDispatcher domainEventDispatcher = mock(ShortLinkDomainEventDispatcher.class);
        LinkTagRepository linkTagRepository = mock(LinkTagRepository.class);
        RedirectCacheSyncPort redirectCacheSync = mock(RedirectCacheSyncPort.class);
        ShortLinkDtoMapper dtoMapper = mock(ShortLinkDtoMapper.class);
        PostCommitHookPort postCommitHookPort = mock(PostCommitHookPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        ApprovalSubmissionPort governanceApprovalRequestService = mock(ApprovalSubmissionPort.class);

        UpdateShortLinkCommandHandler handler = new UpdateShortLinkCommandHandler(
                shortLinkRepository,
                setLinkTagsHandler,
                domainEventDispatcher,
                linkTagRepository,
                redirectCacheSync,
                mock(RedirectCacheInvalidationOutboxPort.class),
                dtoMapper,
                postCommitHookPort,
                clock,
                governanceApprovalRequestService
        );

        ShortLink link = ShortLink.create(
                101L,
                1L,
                2001L,
                3001L,
                ShortCode.of("governed"),
                ShortLinkLifecycleState.ACTIVE,
                HttpUrl.of("https://example.com/old"),
                null,
                true,
                null,
                null,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                9L
        );
        when(shortLinkRepository.findByTenantIdAndId(1L, 101L)).thenReturn(java.util.Optional.of(link));
        when(linkTagRepository.findTagNamesByLinkId(101L)).thenReturn(List.of("alpha"));
        when(governanceApprovalRequestService.requestLinkDestinationChangeApproval(
                eq(1L),
                eq(new ApprovalSubmissionPort.LinkDestinationChangeApprovalRequest(
                        101L,
                        2001L,
                        "https://example.com/old",
                        "https://example.com/new",
                        new ApprovalRequester(1L, 7L, "reviewer@example.com"),
                        LocalDateTime.parse("2026-04-01T00:00:00")
                ))
        )).thenReturn(new com.linkforge.contract.governance.ApprovalRequestView(
                7001L,
                1L,
                "PUBLIC_LINK_DESTINATION_CHANGE",
                2001L,
                7L,
                "reviewer@example.com",
                "PENDING_APPROVAL",
                null,
                null,
                null
        ));

        LinkDto expected = new LinkDto(
                101L,
                1L,
                2001L,
                3001L,
                "ACTIVE",
                "governed-link",
                "https://lf/r/governed-link",
                "https://example.com/old",
                null,
                true,
                null,
                null,
                null,
                false,
                null,
                null,
                List.of(),
                List.of("alpha"),
                Instant.parse("2026-03-31T00:00:00Z")
        );
        when(dtoMapper.toDto(link, List.of("alpha"))).thenReturn(expected);
        LinkDto actual = handler.handle(
                1L,
                101L,
                new UpdateLinkRequest(
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
                ),
                new UserActor(1L, 7L, "reviewer@example.com", Set.of("TENANT_ADMIN")),
                LocalDateTime.parse("2026-04-01T00:00:00")
        );

        assertThat(actual).isEqualTo(expected.withPendingApproval(7001L, "https://example.com/new"));
        verify(governanceApprovalRequestService).requestLinkDestinationChangeApproval(
                1L,
                new ApprovalSubmissionPort.LinkDestinationChangeApprovalRequest(
                        101L,
                        2001L,
                        "https://example.com/old",
                        "https://example.com/new",
                        new ApprovalRequester(1L, 7L, "reviewer@example.com"),
                        LocalDateTime.parse("2026-04-01T00:00:00")
                )
        );
        verify(shortLinkRepository, never()).update(link);
        verify(domainEventDispatcher, never()).publish(eq(link), eq(clock.instant()));
        verify(redirectCacheSync, never()).evict(eq(1L), eq(3001L), anyString());
    }

    @Test
    void handle_shouldRequestDestinationChangeApproval_evenWhenApplicationLinkIsNotActive() {
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        SetLinkTagsCommandHandler setLinkTagsHandler = mock(SetLinkTagsCommandHandler.class);
        ShortLinkDomainEventDispatcher domainEventDispatcher = mock(ShortLinkDomainEventDispatcher.class);
        LinkTagRepository linkTagRepository = mock(LinkTagRepository.class);
        RedirectCacheSyncPort redirectCacheSync = mock(RedirectCacheSyncPort.class);
        ShortLinkDtoMapper dtoMapper = mock(ShortLinkDtoMapper.class);
        PostCommitHookPort postCommitHookPort = mock(PostCommitHookPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        ApprovalSubmissionPort approvalSubmissionPort = mock(ApprovalSubmissionPort.class);

        UpdateShortLinkCommandHandler handler = new UpdateShortLinkCommandHandler(
                shortLinkRepository,
                setLinkTagsHandler,
                domainEventDispatcher,
                linkTagRepository,
                redirectCacheSync,
                mock(RedirectCacheInvalidationOutboxPort.class),
                dtoMapper,
                postCommitHookPort,
                clock,
                approvalSubmissionPort
        );

        ShortLink link = ShortLink.create(
                105L,
                1L,
                2001L,
                3001L,
                ShortCode.of("inactive"),
                ShortLinkLifecycleState.DRAFT,
                HttpUrl.of("https://example.com/old"),
                null,
                true,
                null,
                null,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                9L
        );
        when(shortLinkRepository.findByTenantIdAndId(1L, 105L)).thenReturn(java.util.Optional.of(link));
        when(linkTagRepository.findTagNamesByLinkId(105L)).thenReturn(List.of());
        when(approvalSubmissionPort.requestLinkDestinationChangeApproval(
                eq(1L),
                eq(new ApprovalSubmissionPort.LinkDestinationChangeApprovalRequest(
                        105L,
                        2001L,
                        "https://example.com/old",
                        "https://example.com/new",
                        new ApprovalRequester(1L, 7L, "reviewer@example.com"),
                        LocalDateTime.parse("2026-04-01T00:00:00")
                ))
        )).thenReturn(new com.linkforge.contract.governance.ApprovalRequestView(
                7005L,
                1L,
                "PUBLIC_LINK_DESTINATION_CHANGE",
                2001L,
                7L,
                "reviewer@example.com",
                "PENDING_APPROVAL",
                null,
                null,
                null
        ));
        LinkDto expected = new LinkDto(
                105L,
                1L,
                2001L,
                3001L,
                "DRAFT",
                "inactive",
                "https://lf/r/inactive",
                "https://example.com/old",
                null,
                true,
                null,
                null,
                null,
                false,
                null,
                null,
                List.of(),
                List.of(),
                Instant.parse("2026-03-31T00:00:00Z")
        );
        when(dtoMapper.toDto(link, List.of())).thenReturn(expected);

        LinkDto actual = handler.handle(
                1L,
                105L,
                new UpdateLinkRequest(
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
                ),
                new UserActor(1L, 7L, "reviewer@example.com", Set.of("TENANT_ADMIN")),
                LocalDateTime.parse("2026-04-01T00:00:00")
        );

        assertThat(actual).isEqualTo(expected.withPendingApproval(7005L, "https://example.com/new"));
        verify(approvalSubmissionPort).requestLinkDestinationChangeApproval(
                1L,
                new ApprovalSubmissionPort.LinkDestinationChangeApprovalRequest(
                        105L,
                        2001L,
                        "https://example.com/old",
                        "https://example.com/new",
                        new ApprovalRequester(1L, 7L, "reviewer@example.com"),
                        LocalDateTime.parse("2026-04-01T00:00:00")
                )
        );
        verify(shortLinkRepository, never()).update(link);
    }

    @Test
    void handle_shouldRejectInvalidDestinationUrlBeforeRequestingApproval() {
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        SetLinkTagsCommandHandler setLinkTagsHandler = mock(SetLinkTagsCommandHandler.class);
        ShortLinkDomainEventDispatcher domainEventDispatcher = mock(ShortLinkDomainEventDispatcher.class);
        LinkTagRepository linkTagRepository = mock(LinkTagRepository.class);
        RedirectCacheSyncPort redirectCacheSync = mock(RedirectCacheSyncPort.class);
        ShortLinkDtoMapper dtoMapper = mock(ShortLinkDtoMapper.class);
        PostCommitHookPort postCommitHookPort = mock(PostCommitHookPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        ApprovalSubmissionPort governanceApprovalRequestService = mock(ApprovalSubmissionPort.class);

        UpdateShortLinkCommandHandler handler = new UpdateShortLinkCommandHandler(
                shortLinkRepository,
                setLinkTagsHandler,
                domainEventDispatcher,
                linkTagRepository,
                redirectCacheSync,
                mock(RedirectCacheInvalidationOutboxPort.class),
                dtoMapper,
                postCommitHookPort,
                clock,
                governanceApprovalRequestService
        );

        ShortLink link = ShortLink.create(
                104L,
                1L,
                2001L,
                3001L,
                ShortCode.of("governed4"),
                ShortLinkLifecycleState.ACTIVE,
                HttpUrl.of("https://example.com/old"),
                null,
                true,
                null,
                null,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                9L
        );
        when(shortLinkRepository.findByTenantIdAndId(1L, 104L)).thenReturn(java.util.Optional.of(link));
        when(linkTagRepository.findTagNamesByLinkId(104L)).thenReturn(List.of());

        assertThatThrownBy(() -> handler.handle(
                1L,
                104L,
                new UpdateLinkRequest(
                        "https://example.com/new\noriginalUrl=https://evil.example",
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
                ),
                new UserActor(1L, 7L, "reviewer@example.com", Set.of("TENANT_ADMIN")),
                LocalDateTime.parse("2026-04-01T00:00:00")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("URL");

        verifyNoInteractions(governanceApprovalRequestService);
        verify(shortLinkRepository, never()).update(link);
        verify(domainEventDispatcher, never()).publish(eq(link), eq(clock.instant()));
    }

    @Test
    void handle_shouldRejectMixingDestinationApprovalWithOtherEdits() {
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        SetLinkTagsCommandHandler setLinkTagsHandler = mock(SetLinkTagsCommandHandler.class);
        ShortLinkDomainEventDispatcher domainEventDispatcher = mock(ShortLinkDomainEventDispatcher.class);
        LinkTagRepository linkTagRepository = mock(LinkTagRepository.class);
        RedirectCacheSyncPort redirectCacheSync = mock(RedirectCacheSyncPort.class);
        ShortLinkDtoMapper dtoMapper = mock(ShortLinkDtoMapper.class);
        PostCommitHookPort postCommitHookPort = mock(PostCommitHookPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        ApprovalSubmissionPort governanceApprovalRequestService = mock(ApprovalSubmissionPort.class);

        UpdateShortLinkCommandHandler handler = new UpdateShortLinkCommandHandler(
                shortLinkRepository,
                setLinkTagsHandler,
                domainEventDispatcher,
                linkTagRepository,
                redirectCacheSync,
                mock(RedirectCacheInvalidationOutboxPort.class),
                dtoMapper,
                postCommitHookPort,
                clock,
                governanceApprovalRequestService
        );

        ShortLink link = ShortLink.create(
                102L,
                1L,
                2001L,
                3001L,
                ShortCode.of("governed2"),
                ShortLinkLifecycleState.ACTIVE,
                HttpUrl.of("https://example.com/old"),
                "old-note",
                true,
                null,
                null,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                9L
        );
        when(shortLinkRepository.findByTenantIdAndId(1L, 102L)).thenReturn(java.util.Optional.of(link));

        assertThatThrownBy(() -> handler.handle(
                1L,
                102L,
                new UpdateLinkRequest(
                        "https://example.com/new",
                        "new-note",
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
                ),
                new UserActor(1L, 7L, "reviewer@example.com", Set.of("TENANT_ADMIN")),
                LocalDateTime.parse("2026-04-01T00:00:00")
        )).hasMessageContaining("请先单独提交目标地址变更");

        verify(governanceApprovalRequestService, never()).requestLinkDestinationChangeApproval(
                eq(1L),
                eq(new ApprovalSubmissionPort.LinkDestinationChangeApprovalRequest(
                        102L,
                        2001L,
                        "https://example.com/old",
                        "https://example.com/new",
                        new ApprovalRequester(1L, 7L, "reviewer@example.com"),
                        LocalDateTime.parse("2026-04-01T00:00:00")
                ))
        );
        verify(shortLinkRepository, never()).update(link);
        verify(domainEventDispatcher, never()).publish(eq(link), eq(clock.instant()));
    }

    @Test
    void handle_shouldTranslateInvalidLifecycleState_whenCheckingDestinationApprovalSideEffects() {
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        SetLinkTagsCommandHandler setLinkTagsHandler = mock(SetLinkTagsCommandHandler.class);
        ShortLinkDomainEventDispatcher domainEventDispatcher = mock(ShortLinkDomainEventDispatcher.class);
        LinkTagRepository linkTagRepository = mock(LinkTagRepository.class);
        RedirectCacheSyncPort redirectCacheSync = mock(RedirectCacheSyncPort.class);
        ShortLinkDtoMapper dtoMapper = mock(ShortLinkDtoMapper.class);
        PostCommitHookPort postCommitHookPort = mock(PostCommitHookPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        ApprovalSubmissionPort governanceApprovalRequestService = mock(ApprovalSubmissionPort.class);

        UpdateShortLinkCommandHandler handler = new UpdateShortLinkCommandHandler(
                shortLinkRepository,
                setLinkTagsHandler,
                domainEventDispatcher,
                linkTagRepository,
                redirectCacheSync,
                mock(RedirectCacheInvalidationOutboxPort.class),
                dtoMapper,
                postCommitHookPort,
                clock,
                governanceApprovalRequestService
        );

        ShortLink link = ShortLink.create(
                103L,
                1L,
                2001L,
                3001L,
                ShortCode.of("governed3"),
                ShortLinkLifecycleState.ACTIVE,
                HttpUrl.of("https://example.com/old"),
                null,
                true,
                null,
                null,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                9L
        );
        when(shortLinkRepository.findByTenantIdAndId(1L, 103L)).thenReturn(java.util.Optional.of(link));
        when(linkTagRepository.findTagNamesByLinkId(103L)).thenReturn(List.of());

        assertThatThrownBy(() -> handler.handle(
                1L,
                103L,
                new UpdateLinkRequest(
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
                        "not-a-state"
                ),
                new UserActor(1L, 7L, "reviewer@example.com", Set.of("TENANT_ADMIN")),
                LocalDateTime.parse("2026-04-01T00:00:00")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST))
                .hasMessageContaining("lifecycleState");

        verifyNoInteractions(governanceApprovalRequestService);
        verify(shortLinkRepository, never()).update(link);
        verify(domainEventDispatcher, never()).publish(eq(link), eq(clock.instant()));
    }

    @Test
    void handle_shouldRejectExpiresAtValueTogetherWithClearFlag() {
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        ShortLinkDomainEventDispatcher domainEventDispatcher = mock(ShortLinkDomainEventDispatcher.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        UpdateShortLinkCommandHandler handler = new UpdateShortLinkCommandHandler(
                shortLinkRepository,
                mock(SetLinkTagsCommandHandler.class),
                domainEventDispatcher,
                mock(LinkTagRepository.class),
                mock(RedirectCacheSyncPort.class),
                mock(RedirectCacheInvalidationOutboxPort.class),
                mock(ShortLinkDtoMapper.class),
                mock(PostCommitHookPort.class),
                clock,
                mock(ApprovalSubmissionPort.class)
        );
        ShortLink link = ShortLink.create(
                104L,
                1L,
                null,
                null,
                ShortCode.of("expiry1"),
                ShortLinkLifecycleState.ACTIVE,
                HttpUrl.of("https://example.com/old"),
                null,
                true,
                null,
                null,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                7L
        );
        when(shortLinkRepository.findByTenantIdAndId(1L, 104L)).thenReturn(java.util.Optional.of(link));

        assertThatThrownBy(() -> handler.handle(
                1L,
                104L,
                new UpdateLinkRequest(
                        null,
                        null,
                        Instant.parse("2026-05-01T00:00:00Z"),
                        true,
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
                ),
                new UserActor(1L, 7L, "owner@example.com", Set.of("USER")),
                LocalDateTime.parse("2026-04-01T00:00:00")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST))
                .hasMessageContaining("clearExpiresAt");

        verify(shortLinkRepository, never()).update(link);
        verifyNoInteractions(domainEventDispatcher);
    }
}
