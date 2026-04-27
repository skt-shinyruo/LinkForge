package com.linkforge.shortlink.application;

import com.linkforge.contract.shortlink.ShortLinkReadPort;
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
        ShortLinkReadPort service = new ShortLinkReadApplicationService(repository);

        ShortLinkReadPort.RedirectLinkView expected = new ShortLinkReadPort.RedirectLinkView(
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

        Optional<ShortLinkReadPort.RedirectLinkView> actual =
                service.findRedirectMetaByHostAndCode("go.example.test", "abc123");

        assertThat(actual).contains(expected);
        verify(repository).findRedirectMetaByHostAndCode("go.example.test", "abc123");
    }

    @Test
    void findOwnership_shouldDelegateToRepository() {
        ShortLinkReadRepository repository = mock(ShortLinkReadRepository.class);
        ShortLinkReadPort service = new ShortLinkReadApplicationService(repository);
        ShortLinkReadPort.ShortLinkOwnership expected = new ShortLinkReadPort.ShortLinkOwnership(33L, 44L);
        when(repository.findOwnership(22L, 11L)).thenReturn(Optional.of(expected));

        Optional<ShortLinkReadPort.ShortLinkOwnership> actual = service.findOwnership(22L, 11L);

        assertThat(actual).contains(expected);
        verify(repository).findOwnership(22L, 11L);
    }

    @Test
    void listSummaries_shouldReturnRepositoryValues() {
        ShortLinkReadRepository repository = mock(ShortLinkReadRepository.class);
        ShortLinkReadPort service = new ShortLinkReadApplicationService(repository);
        Map<Long, ShortLinkReadPort.ShortLinkSummary> expected = Map.of(
                101L, new ShortLinkReadPort.ShortLinkSummary(101L, "abc123", "https://example.com/a", false),
                102L, new ShortLinkReadPort.ShortLinkSummary(102L, "xyz789", "https://example.com/b", false)
        );
        when(repository.listSummaries(1L, List.of(101L, 102L))).thenReturn(expected);

        Map<Long, ShortLinkReadPort.ShortLinkSummary> actual = service.listSummaries(1L, List.of(101L, 102L));

        assertThat(actual).isEqualTo(expected);
        verify(repository).listSummaries(1L, List.of(101L, 102L));
    }

}
