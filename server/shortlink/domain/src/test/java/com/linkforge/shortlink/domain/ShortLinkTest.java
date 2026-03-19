package com.linkforge.shortlink.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortLinkTest {

    @Test
    void archive_withNullNowUtc_shouldRejectImplicitLocalTimeFallback() {
        ShortLink link = ShortLink.create(
                1L,
                1L,
                ShortCode.of("abc123"),
                HttpUrl.of("https://example.com/path"),
                "note",
                true,
                null,
                null,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                99L
        );

        assertThatThrownBy(() -> link.archive(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("nowUtc");
        assertThat(link.archivedAtUtc()).isNull();
    }
}
