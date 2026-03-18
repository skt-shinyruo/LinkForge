package com.linkforge.shortlink.interfaces.web;

import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.shortlink.application.ShortLinkService;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkCreateHttpRequest;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkPageHttpResponse;
import com.linkforge.shortlink.interfaces.web.dto.ShortLinkUpdateHttpRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShortLinkHttpMapperTest {

    @Test
    void toCreateRequest_shouldTranslateHttpRequest() {
        ShortLinkCreateHttpRequest httpRequest = new ShortLinkCreateHttpRequest(
                "https://example.com/source",
                "launch note",
                Instant.parse("2026-03-18T09:10:11Z"),
                true,
                "launch",
                Set.of("marketing", "spring"),
                302,
                true,
                "https://example.com/unavailable",
                "ALLOWLIST",
                List.of("utm_*", "ref")
        );

        assertThat(ShortLinkHttpMapper.toCreateRequest(httpRequest)).isEqualTo(
                new ShortLinkService.CreateLinkRequest(
                        "https://example.com/source",
                        "launch note",
                        Instant.parse("2026-03-18T09:10:11Z"),
                        true,
                        "launch",
                        Set.of("marketing", "spring"),
                        302,
                        true,
                        "https://example.com/unavailable",
                        "ALLOWLIST",
                        List.of("utm_*", "ref")
                )
        );
    }

    @Test
    void toUpdateRequest_shouldTranslateHttpRequest() {
        ShortLinkUpdateHttpRequest httpRequest = new ShortLinkUpdateHttpRequest(
                "https://example.com/updated",
                "updated note",
                Instant.parse("2026-04-01T01:02:03Z"),
                true,
                false,
                Set.of("ops"),
                301,
                true,
                false,
                "https://example.com/fallback",
                "DROP",
                true,
                List.of("utm_campaign")
        );

        assertThat(ShortLinkHttpMapper.toUpdateRequest(httpRequest)).isEqualTo(
                new ShortLinkService.UpdateLinkRequest(
                        "https://example.com/updated",
                        "updated note",
                        Instant.parse("2026-04-01T01:02:03Z"),
                        true,
                        false,
                        Set.of("ops"),
                        301,
                        true,
                        false,
                        "https://example.com/fallback",
                        "DROP",
                        true,
                        List.of("utm_campaign")
                )
        );
    }

    @Test
    void toPageResponse_shouldPreserveItemsAndPagination() {
        ShortLinkService.LinkDto link = new ShortLinkService.LinkDto(
                42L,
                7L,
                "launch",
                "https://lnk.forge/launch",
                "https://example.com/source",
                "launch note",
                true,
                Instant.parse("2026-04-01T01:02:03Z"),
                null,
                302,
                true,
                "https://example.com/unavailable",
                "ALLOWLIST",
                List.of("utm_*"),
                List.of("marketing"),
                Instant.parse("2026-03-18T09:10:11Z")
        );
        PageResult<ShortLinkService.LinkDto> result = new PageResult<>(List.of(link), 11L, 2, 5);

        assertThat(ShortLinkHttpMapper.toPageResponse(result)).isEqualTo(
                new ShortLinkPageHttpResponse<>(List.of(link), 11L, 2, 5)
        );
    }
}
