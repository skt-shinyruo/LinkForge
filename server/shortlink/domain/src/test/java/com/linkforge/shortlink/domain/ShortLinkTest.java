package com.linkforge.shortlink.domain;

import com.linkforge.shortlink.domain.event.ShortLinkArchived;
import com.linkforge.shortlink.domain.event.ShortLinkDeleted;
import com.linkforge.shortlink.domain.event.ShortLinkRestored;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortLinkTest {

    @Test
    void archive_withNullNowUtc_shouldRejectImplicitLocalTimeFallback() {
        ShortLink link = activeLink();

        assertThatThrownBy(() -> link.archive(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("nowUtc");
        assertThat(link.archivedAtUtc()).isNull();
    }

    @Test
    void archive_shouldRecordDomainEventOnlyWhenStateChanges() {
        ShortLink link = activeLink();
        LocalDateTime archivedAtUtc = LocalDateTime.parse("2026-04-28T01:02:03");

        assertThat(link.archive(archivedAtUtc)).isTrue();
        assertThat(link.archive(LocalDateTime.parse("2026-04-28T02:03:04"))).isFalse();

        assertThat(link.archivedAtUtc()).isEqualTo(archivedAtUtc);
        assertThat(link.pullDomainEvents())
                .containsExactly(new ShortLinkArchived(1L, 1L, null, "abc123", archivedAtUtc));
        assertThat(link.pullDomainEvents()).isEmpty();
    }

    @Test
    void restore_shouldRecordDomainEventOnlyWhenLinkWasArchived() {
        ShortLink link = activeLink();
        link.archive(LocalDateTime.parse("2026-04-28T01:02:03"));
        link.pullDomainEvents();

        assertThat(link.restore()).isTrue();
        assertThat(link.restore()).isFalse();

        assertThat(link.archivedAtUtc()).isNull();
        assertThat(link.pullDomainEvents())
                .containsExactly(new ShortLinkRestored(1L, 1L, null, "abc123"));
    }

    @Test
    void markDeleted_shouldRequireArchiveAndRecordDomainEvent() {
        ShortLink link = activeLink();
        LocalDateTime deletedAtUtc = LocalDateTime.parse("2026-04-28T03:04:05");

        assertThatThrownBy(() -> link.markDeleted(deletedAtUtc))
                .isInstanceOf(ShortLinkDomainException.class)
                .hasMessageContaining("删除前请先归档");

        link.archive(LocalDateTime.parse("2026-04-28T01:02:03"));
        link.pullDomainEvents();

        link.markDeleted(deletedAtUtc);

        assertThat(link.pullDomainEvents())
                .containsExactly(new ShortLinkDeleted(1L, 1L, null, "abc123", deletedAtUtc));
    }

    @Test
    void pullDomainEvents_shouldReturnImmutableSnapshotAndClearAggregateEvents() {
        ShortLink link = activeLink();
        link.archive(LocalDateTime.parse("2026-04-28T01:02:03"));

        List<?> events = link.pullDomainEvents();

        assertThatThrownBy(() -> events.clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(link.pullDomainEvents()).isEmpty();
    }

    private static ShortLink activeLink() {
        return ShortLink.create(
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
    }
}
