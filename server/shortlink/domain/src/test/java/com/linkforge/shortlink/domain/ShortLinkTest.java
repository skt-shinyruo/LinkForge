package com.linkforge.shortlink.domain;

import com.linkforge.shortlink.domain.event.ShortLinkArchived;
import com.linkforge.shortlink.domain.event.ShortLinkCreated;
import com.linkforge.shortlink.domain.event.ShortLinkDeleted;
import com.linkforge.shortlink.domain.event.ShortLinkOwnershipChanged;
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
        assertThat(link.version()).isEqualTo(1L);
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
        assertThat(link.version()).isEqualTo(2L);
        assertThat(link.pullDomainEvents())
                .containsExactly(new ShortLinkRestored(1L, 1L, null, "abc123"));
    }

    @Test
    void delete_shouldRequireArchiveAndRecordDomainEvent() {
        ShortLink link = activeLink();
        link.pullDomainEvents();
        LocalDateTime deletedAtUtc = LocalDateTime.parse("2026-04-28T03:04:05");

        assertThatThrownBy(() -> link.delete(deletedAtUtc))
                .isInstanceOf(ShortLinkDomainException.class)
                .hasMessageContaining("删除前请先归档");
        assertThat(link.version()).isZero();
        assertThat(link.pullDomainEvents()).isEmpty();

        link.archive(LocalDateTime.parse("2026-04-28T01:02:03"));
        link.pullDomainEvents();

        assertThat(link.delete(deletedAtUtc)).isTrue();
        assertThat(link.delete(deletedAtUtc.plusSeconds(1))).isFalse();

        assertThat(link.version()).isEqualTo(2L);
        assertThat(link.pullDomainEvents())
                .containsExactly(new ShortLinkDeleted(1L, 1L, null, "abc123", deletedAtUtc));
    }

    @Test
    void applyUpdate_shouldOwnGuardVersionTimestampAndSingleEvent() {
        ShortLink link = rehydratedLink(ShortLinkLifecycleState.ACTIVE, null, null, null, 3L);
        LocalDateTime updatedAtUtc = LocalDateTime.parse("2026-04-28T04:05:06");
        ShortLinkPatch patch = patchOriginalUrl("https://example.com/updated");

        ShortLinkChangeSet changes = link.applyUpdate(patch, false, updatedAtUtc);

        assertThat(changes.fields()).containsExactly(ShortLinkChangeSet.Field.ORIGINAL_URL);
        assertThat(link.originalUrl()).isEqualTo(HttpUrl.of("https://example.com/updated"));
        assertThat(link.updatedAtUtc()).isEqualTo(updatedAtUtc);
        assertThat(link.version()).isEqualTo(4L);
        assertThat(link.pullDomainEvents())
                .containsExactly(new ShortLinkUpdated(1L, 1L, null, "abc123", updatedAtUtc));

        assertThat(link.applyUpdate(patch, false, updatedAtUtc.plusSeconds(1)).hasChanges()).isFalse();
        assertThat(link.version()).isEqualTo(4L);
        assertThat(link.pullDomainEvents()).isEmpty();
    }

    @Test
    void applyUpdate_shouldRecordRelatedTagChangeWithoutFieldMutation() {
        ShortLink link = rehydratedLink(ShortLinkLifecycleState.ACTIVE, null, null, null, 3L);
        LocalDateTime updatedAtUtc = LocalDateTime.parse("2026-04-28T04:05:06");

        ShortLinkChangeSet changes = link.applyUpdate(emptyPatch(), true, updatedAtUtc);

        assertThat(changes.hasChanges()).isFalse();
        assertThat(link.version()).isEqualTo(4L);
        assertThat(link.updatedAtUtc()).isEqualTo(updatedAtUtc);
        assertThat(link.pullDomainEvents())
                .containsExactly(new ShortLinkUpdated(1L, 1L, null, "abc123", updatedAtUtc));
    }

    @Test
    void applyUpdate_whenArchived_shouldRejectWithoutPartialMutation() {
        ShortLink link = rehydratedLink(
                ShortLinkLifecycleState.ACTIVE,
                null,
                null,
                LocalDateTime.parse("2026-04-28T01:02:03"),
                3L
        );

        assertThatThrownBy(() -> link.applyUpdate(
                patchOriginalUrl("https://example.com/rejected"),
                false,
                LocalDateTime.parse("2026-04-28T04:05:06")
        )).isInstanceOf(ShortLinkDomainException.class)
                .hasMessageContaining("已归档");

        assertThat(link.originalUrl()).isEqualTo(HttpUrl.of("https://example.com/path"));
        assertThat(link.version()).isEqualTo(3L);
        assertThat(link.pullDomainEvents()).isEmpty();
    }

    @Test
    void approveDestinationChange_shouldRequirePublishableScopedLinkAndBeIdempotent() {
        ShortLink link = rehydratedLink(ShortLinkLifecycleState.ACTIVE, 2001L, 3001L, null, 3L);
        LocalDateTime changedAtUtc = LocalDateTime.parse("2026-04-28T05:06:07");
        HttpUrl approvedUrl = HttpUrl.of("https://example.com/approved");

        assertThat(link.approveDestinationChange(approvedUrl, changedAtUtc)).isTrue();
        assertThat(link.approveDestinationChange(approvedUrl, changedAtUtc.plusSeconds(1))).isFalse();

        assertThat(link.originalUrl()).isEqualTo(approvedUrl);
        assertThat(link.version()).isEqualTo(4L);
        assertThat(link.pullDomainEvents())
                .containsExactly(new ShortLinkUpdated(1L, 1L, 3001L, "abc123", changedAtUtc));

        ShortLink draft = rehydratedLink(ShortLinkLifecycleState.DRAFT, 2001L, 3001L, null, 8L);
        assertThatThrownBy(() -> draft.approveDestinationChange(approvedUrl, changedAtUtc))
                .isInstanceOf(ShortLinkDomainException.class)
                .hasMessageContaining("ACTIVE");
        assertThat(draft.version()).isEqualTo(8L);
        assertThat(draft.pullDomainEvents()).isEmpty();
    }

    @Test
    void reconcileOwnership_shouldAdvanceVersionOnceAndCaptureBothScopes() {
        ShortLink link = rehydratedLink(ShortLinkLifecycleState.ACTIVE, null, null, null, 3L);
        LocalDateTime changedAtUtc = LocalDateTime.parse("2026-04-28T06:07:08");

        assertThat(link.reconcileOwnership(2001L, 3001L, changedAtUtc)).isTrue();
        assertThat(link.reconcileOwnership(2001L, 3001L, changedAtUtc.plusSeconds(1))).isFalse();

        assertThat(link.applicationId()).isEqualTo(2001L);
        assertThat(link.domainId()).isEqualTo(3001L);
        assertThat(link.version()).isEqualTo(4L);
        assertThat(link.pullDomainEvents()).containsExactly(new ShortLinkOwnershipChanged(
                1L,
                1L,
                null,
                null,
                2001L,
                3001L,
                "abc123",
                changedAtUtc
        ));

        assertThatThrownBy(() -> link.reconcileOwnership(2002L, null, changedAtUtc.plusSeconds(2)))
                .isInstanceOf(ShortLinkDomainException.class)
                .hasMessageContaining("同时存在");
        assertThatThrownBy(() -> link.reconcileOwnership(2002L, 3002L, changedAtUtc.plusSeconds(3)))
                .isInstanceOf(ShortLinkDomainException.class)
                .hasMessageContaining("不能重新绑定");
        assertThatThrownBy(() -> link.reconcileOwnership(null, null, changedAtUtc.plusSeconds(4)))
                .isInstanceOf(ShortLinkDomainException.class)
                .hasMessageContaining("同时存在");
        assertThat(link.applicationId()).isEqualTo(2001L);
        assertThat(link.domainId()).isEqualTo(3001L);
        assertThat(link.version()).isEqualTo(4L);
        assertThat(link.pullDomainEvents()).isEmpty();
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

    private static ShortLink rehydratedLink(
            ShortLinkLifecycleState lifecycleState,
            Long applicationId,
            Long domainId,
            LocalDateTime archivedAtUtc,
            long version
    ) {
        return ShortLink.rehydrate(
                1L,
                1L,
                applicationId,
                domainId,
                ShortCode.of("abc123"),
                lifecycleState,
                HttpUrl.of("https://example.com/path"),
                "note",
                true,
                null,
                archivedAtUtc,
                null,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                99L,
                version,
                LocalDateTime.parse("2026-04-28T00:00:00"),
                LocalDateTime.parse("2026-04-28T01:00:00")
        );
    }

    private static ShortLinkPatch patchOriginalUrl(String url) {
        return new ShortLinkPatch(
                PatchValue.set(HttpUrl.of(url)),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static ShortLinkPatch emptyPatch() {
        return new ShortLinkPatch(null, null, null, null, null, null, null, null, null, null);
    }
}
