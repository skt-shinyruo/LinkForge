package com.linkforge.shortlink.application.command;

import com.linkforge.contract.governance.ApprovalRequestView;
import com.linkforge.contract.governance.ApprovalSubmissionPort;
import com.linkforge.contract.governance.SensitiveOperation;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.shortlink.application.ShortLinkService;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
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
import static org.mockito.Mockito.when;

class UpdateShortLinkCommandHandlerTest {

    @Test
    void constructor_shouldDependOnApprovalSubmissionPort_insteadOfGovernanceService() {
        Constructor<?> constructor = UpdateShortLinkCommandHandler.class.getDeclaredConstructors()[0];

        assertThat(constructor.getParameterTypes())
                .contains(ApprovalSubmissionPort.class);
        assertThat(constructor.getParameterTypes())
                .extracting(Class::getName)
                .doesNotContain("com.linkforge.governance.application.GovernanceService");
    }

    @Test
    void handle_shouldSubmitDestinationChangeApproval_viaGovernanceContract() {
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        SetLinkTagsCommandHandler setLinkTagsHandler = mock(SetLinkTagsCommandHandler.class);
        ShortLinkEventPublisher eventPublisher = mock(ShortLinkEventPublisher.class);
        LinkTagRepository linkTagRepository = mock(LinkTagRepository.class);
        RedirectCacheSyncPort redirectCacheSync = mock(RedirectCacheSyncPort.class);
        ShortLinkDtoMapper dtoMapper = mock(ShortLinkDtoMapper.class);
        PostCommitHookPort postCommitHookPort = mock(PostCommitHookPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        ApprovalSubmissionPort approvalSubmissionPort = mock(ApprovalSubmissionPort.class);

        UpdateShortLinkCommandHandler handler = new UpdateShortLinkCommandHandler(
                shortLinkRepository,
                setLinkTagsHandler,
                eventPublisher,
                linkTagRepository,
                redirectCacheSync,
                dtoMapper,
                postCommitHookPort,
                clock,
                approvalSubmissionPort
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

        ShortLinkService.LinkDto expected = new ShortLinkService.LinkDto(
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
        when(approvalSubmissionPort.submitRequest(
                1L,
                SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE,
                2001L,
                "originalUrl=https://example.com/old",
                "originalUrl=https://example.com/new",
                1L,
                7L,
                "reviewer@example.com",
                Set.of("TENANT_ADMIN"),
                LocalDateTime.parse("2026-04-01T00:00:00")
        )).thenReturn(new ApprovalRequestView(
                501L,
                1L,
                SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE,
                2001L,
                7L,
                "reviewer@example.com",
                "PENDING_APPROVAL",
                null,
                null,
                null
        ));

        ShortLinkService.LinkDto actual = handler.handle(
                1L,
                101L,
                new ShortLinkService.UpdateLinkRequest(
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

        assertThat(actual).isSameAs(expected);
        verify(approvalSubmissionPort).submitRequest(
                1L,
                SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE,
                2001L,
                "originalUrl=https://example.com/old",
                "originalUrl=https://example.com/new",
                1L,
                7L,
                "reviewer@example.com",
                Set.of("TENANT_ADMIN"),
                LocalDateTime.parse("2026-04-01T00:00:00")
        );
        verify(shortLinkRepository, never()).update(link);
        verify(eventPublisher, never()).updated(eq(link), eq(clock.instant()));
        verify(redirectCacheSync, never()).evict(eq(1L), eq(3001L), anyString());
    }

    @Test
    void handle_shouldRejectMixingDestinationApprovalWithOtherEdits() {
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        SetLinkTagsCommandHandler setLinkTagsHandler = mock(SetLinkTagsCommandHandler.class);
        ShortLinkEventPublisher eventPublisher = mock(ShortLinkEventPublisher.class);
        LinkTagRepository linkTagRepository = mock(LinkTagRepository.class);
        RedirectCacheSyncPort redirectCacheSync = mock(RedirectCacheSyncPort.class);
        ShortLinkDtoMapper dtoMapper = mock(ShortLinkDtoMapper.class);
        PostCommitHookPort postCommitHookPort = mock(PostCommitHookPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        ApprovalSubmissionPort approvalSubmissionPort = mock(ApprovalSubmissionPort.class);

        UpdateShortLinkCommandHandler handler = new UpdateShortLinkCommandHandler(
                shortLinkRepository,
                setLinkTagsHandler,
                eventPublisher,
                linkTagRepository,
                redirectCacheSync,
                dtoMapper,
                postCommitHookPort,
                clock,
                approvalSubmissionPort
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
                new ShortLinkService.UpdateLinkRequest(
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

        verify(approvalSubmissionPort, never()).submitRequest(
                eq(1L),
                eq(SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE),
                eq(2001L),
                anyString(),
                anyString(),
                eq(1L),
                eq(7L),
                eq("reviewer@example.com"),
                eq(Set.of("TENANT_ADMIN")),
                eq(LocalDateTime.parse("2026-04-01T00:00:00"))
        );
        verify(shortLinkRepository, never()).update(link);
        verify(eventPublisher, never()).updated(eq(link), eq(clock.instant()));
    }
}
