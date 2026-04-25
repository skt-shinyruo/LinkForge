package com.linkforge.shortlink.application;

import com.linkforge.shortlink.application.port.ShortLinkReadRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShortLinkReadApplicationServiceTest {

    @Test
    void findRedirectMetaByHostAndCode_shouldReturnRedirectFieldsFromRepository() {
        ShortLinkReadRepository repository = mock(ShortLinkReadRepository.class);
        ShortLinkReadService service = new ShortLinkReadApplicationService(repository);

        ShortLinkReadService.RedirectLinkMeta expected = new ShortLinkReadService.RedirectLinkMeta(
                22L,
                11L,
                "abc123",
                "go.example.test",
                "https://example.com/live",
                true,
                Instant.parse("2026-03-18T10:15:30Z"),
                302,
                false,
                "https://example.com/unavailable",
                "ALLOWLIST",
                "utm_source,utm_medium",
                33L,
                44L
        );
        when(repository.findRedirectMetaByHostAndCode("go.example.test", "abc123"))
                .thenReturn(Optional.of(expected));

        Optional<ShortLinkReadService.RedirectLinkMeta> actual =
                service.findRedirectMetaByHostAndCode("go.example.test", "abc123");

        assertThat(actual).contains(expected);
        verify(repository).findRedirectMetaByHostAndCode("go.example.test", "abc123");
    }

    @Test
    void findOwnership_shouldDelegateToRepository() {
        ShortLinkReadRepository repository = mock(ShortLinkReadRepository.class);
        ShortLinkReadService service = new ShortLinkReadApplicationService(repository);
        ShortLinkReadService.LinkOwnership expected = new ShortLinkReadService.LinkOwnership(33L, 44L);
        when(repository.findOwnership(22L, 11L)).thenReturn(Optional.of(expected));

        Optional<ShortLinkReadService.LinkOwnership> actual = service.findOwnership(22L, 11L);

        assertThat(actual).contains(expected);
        verify(repository).findOwnership(22L, 11L);
    }

    @Test
    void listSummaries_shouldReturnRepositoryValues() {
        ShortLinkReadRepository repository = mock(ShortLinkReadRepository.class);
        ShortLinkReadService service = new ShortLinkReadApplicationService(repository);
        Map<Long, ShortLinkReadService.LinkSummary> expected = Map.of(
                101L, new ShortLinkReadService.LinkSummary(101L, "abc123", "https://example.com/a", false),
                102L, new ShortLinkReadService.LinkSummary(102L, "xyz789", "https://example.com/b", false)
        );
        when(repository.listSummaries(1L, List.of(101L, 102L))).thenReturn(expected);

        Map<Long, ShortLinkReadService.LinkSummary> actual = service.listSummaries(1L, List.of(101L, 102L));

        assertThat(actual).isEqualTo(expected);
        verify(repository).listSummaries(1L, List.of(101L, 102L));
    }

    @Test
    void scopeLookups_shouldDelegateToRepository() {
        ShortLinkReadRepository repository = mock(ShortLinkReadRepository.class);
        ShortLinkReadService service = new ShortLinkReadApplicationService(repository);
        when(repository.listLinkIdsByApplication(1L, 2001L)).thenReturn(List.of(101L, 102L));
        when(repository.listLinkIdsByDomain(1L, 3001L)).thenReturn(List.of(201L));

        assertThat(service.listLinkIdsByApplication(1L, 2001L)).containsExactly(101L, 102L);
        assertThat(service.listLinkIdsByDomain(1L, 3001L)).containsExactly(201L);

        verify(repository).listLinkIdsByApplication(1L, 2001L);
        verify(repository).listLinkIdsByDomain(1L, 3001L);
    }
}
