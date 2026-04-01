package com.linkforge.shortlink.application.command;

import com.linkforge.contract.governance.ApprovalRequestView;
import com.linkforge.contract.governance.ApprovalSubmissionPort;
import com.linkforge.contract.governance.SensitiveOperation;
import com.linkforge.foundation.runtime.security.TenantGuard;
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
        TenantGuard tenantGuard = mock(TenantGuard.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        ApprovalSubmissionPort approvalSubmissionPort = mock(ApprovalSubmissionPort.class);

        UpdateShortLinkCommandHandler handler = new UpdateShortLinkCommandHandler(
                shortLinkRepository,
                setLinkTagsHandler,
                eventPublisher,
                linkTagRepository,
                redirectCacheSync,
                dtoMapper,
                tenantGuard,
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
                "originalUrl=https://example.com/new"
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
                )
        );

        assertThat(actual).isSameAs(expected);
        verify(approvalSubmissionPort).submitRequest(
                1L,
                SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE,
                2001L,
                "originalUrl=https://example.com/old",
                "originalUrl=https://example.com/new"
        );
        verify(shortLinkRepository, never()).update(link);
        verify(eventPublisher, never()).updated(eq(link), eq(clock.instant()));
        verify(redirectCacheSync, never()).evict(eq(1L), eq(3001L), anyString());
    }
}
