package com.linkforge.shortlink.domain;

import com.linkforge.shortlink.domain.event.ShortLinkArchived;
import com.linkforge.shortlink.domain.event.ShortLinkCreated;
import com.linkforge.shortlink.domain.event.ShortLinkDeleted;
import com.linkforge.shortlink.domain.event.ShortLinkRestored;
import com.linkforge.shortlink.domain.event.ShortLinkUpdated;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortLinkTest {

    @Test
    void create_shouldRecordCreatedDomainEvent() {
        ShortLink link = activeLink();

        assertThat(link.pullDomainEvents())
                .containsExactly(new ShortLinkCreated(1L, 1L, null, "abc123"));
    }

    @Test
    void rehydrate_shouldNotRecordCreatedDomainEvent() {
        ShortLink link = ShortLink.rehydrate(
                1L,
                1L,
                ShortCode.of("abc123"),
                HttpUrl.of("https://example.com/path"),
                "note",
                true,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                99L,
                3L,
                LocalDateTime.parse("2026-04-28T01:02:03"),
                LocalDateTime.parse("2026-04-28T01:02:03")
        );

        assertThat(link.pullDomainEvents()).isEmpty();
    }

    @Test
    void markUpdated_shouldRecordUpdatedDomainEvent() {
        ShortLink link = activeLink();
        link.pullDomainEvents();
        LocalDateTime updatedAtUtc = LocalDateTime.parse("2026-04-28T04:05:06");

        link.markUpdated(updatedAtUtc);

        assertThat(link.pullDomainEvents())
                .containsExactly(new ShortLinkUpdated(1L, 1L, null, "abc123", updatedAtUtc));
    }

    @Test
    void markUpdated_withNullNowUtc_shouldRejectImplicitLocalTimeFallback() {
        ShortLink link = activeLink();
        link.pullDomainEvents();

        assertThatThrownBy(() -> link.markUpdated(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("updatedAtUtc");
        assertThat(link.pullDomainEvents()).isEmpty();
    }

    @Test
    void archive_withNullNowUtc_shouldRejectImplicitLocalTimeFallback() {
        ShortLink link = activeLink();
        link.pullDomainEvents();

        assertThatThrownBy(() -> link.archive(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("nowUtc");
        assertThat(link.archivedAtUtc()).isNull();
    }

    @Test
    void archive_shouldRecordDomainEventOnlyWhenStateChanges() {
        ShortLink link = activeLink();
        link.pullDomainEvents();
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
        link.pullDomainEvents();
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
        link.pullDomainEvents();
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
        link.pullDomainEvents();
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
