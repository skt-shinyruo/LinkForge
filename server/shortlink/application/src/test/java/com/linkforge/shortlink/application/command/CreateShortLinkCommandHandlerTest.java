package com.linkforge.shortlink.application.command;

import com.linkforge.contract.platform.ApplicationQuotaView;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.runtime.security.TenantGuard;
import com.linkforge.shortlink.application.ShortLinkService;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    void handle_shouldValidateApplicationScopeAndQuota_viaPlatformContract() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(101L);

        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        SetLinkTagsCommandHandler setLinkTagsHandler = mock(SetLinkTagsCommandHandler.class);
        LinkTagRepository linkTagRepository = mock(LinkTagRepository.class);
        ShortLinkEventPublisher eventPublisher = mock(ShortLinkEventPublisher.class);
        RedirectCacheSyncPort redirectCacheSync = mock(RedirectCacheSyncPort.class);
        ShortLinkDtoMapper dtoMapper = mock(ShortLinkDtoMapper.class);
        TenantGuard tenantGuard = mock(TenantGuard.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        ApplicationScopePort applicationScopePort = mock(ApplicationScopePort.class);

        CreateShortLinkCommandHandler handler = new CreateShortLinkCommandHandler(
                idGenerator,
                shortLinkRepository,
                setLinkTagsHandler,
                linkTagRepository,
                eventPublisher,
                redirectCacheSync,
                dtoMapper,
                tenantGuard,
                clock,
                applicationScopePort
        );

        when(applicationScopePort.findApplicationQuota(1L, 2001L))
                .thenReturn(Optional.of(new ApplicationQuotaView(2001L, 10L, 100L)));
        when(shortLinkRepository.countActiveByTenantIdAndApplicationId(1L, 2001L)).thenReturn(2L);
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
        verify(shortLinkRepository).countActiveByTenantIdAndApplicationId(1L, 2001L);
    }
}
