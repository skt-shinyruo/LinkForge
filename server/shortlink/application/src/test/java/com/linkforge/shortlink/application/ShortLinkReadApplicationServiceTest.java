package com.linkforge.shortlink.application;

import com.linkforge.shortlink.application.port.ShortLinkReadRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
}
