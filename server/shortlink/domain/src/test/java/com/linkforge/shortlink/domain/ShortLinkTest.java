package com.linkforge.shortlink.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

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
    void archive_shouldChangeStateOnlyOnce() {
        ShortLink link = activeLink();
        LocalDateTime archivedAtUtc = LocalDateTime.parse("2026-04-28T01:02:03");

        assertThat(link.archive(archivedAtUtc)).isTrue();
        assertThat(link.archive(LocalDateTime.parse("2026-04-28T02:03:04"))).isFalse();

        assertThat(link.archivedAtUtc()).isEqualTo(archivedAtUtc);
        assertThat(link.version()).isEqualTo(1L);
    }

    @Test
    void restore_shouldChangeStateOnlyWhenLinkWasArchived() {
        ShortLink link = activeLink();
        link.archive(LocalDateTime.parse("2026-04-28T01:02:03"));

        assertThat(link.restore()).isTrue();
        assertThat(link.restore()).isFalse();

        assertThat(link.archivedAtUtc()).isNull();
        assertThat(link.version()).isEqualTo(2L);
    }

    @Test
    void delete_shouldRequireArchiveAndAdvanceVersionOnce() {
        ShortLink link = activeLink();
        LocalDateTime deletedAtUtc = LocalDateTime.parse("2026-04-28T03:04:05");

        assertThatThrownBy(() -> link.delete(deletedAtUtc))
                .isInstanceOf(ShortLinkDomainException.class)
                .hasMessageContaining("删除前请先归档");
        assertThat(link.version()).isZero();

        link.archive(LocalDateTime.parse("2026-04-28T01:02:03"));

        assertThat(link.delete(deletedAtUtc)).isTrue();
        assertThat(link.delete(deletedAtUtc.plusSeconds(1))).isFalse();

        assertThat(link.version()).isEqualTo(2L);
    }

    @Test
    void applyUpdate_shouldOwnGuardVersionAndTimestamp() {
        ShortLink link = rehydratedLink(ShortLinkLifecycleState.ACTIVE, null, null, null, 3L);
        LocalDateTime updatedAtUtc = LocalDateTime.parse("2026-04-28T04:05:06");
        ShortLinkPatch patch = patchOriginalUrl("https://example.com/updated");

        ShortLinkChangeSet changes = link.applyUpdate(patch, false, updatedAtUtc);

        assertThat(changes.fields()).containsExactly(ShortLinkChangeSet.Field.ORIGINAL_URL);
        assertThat(link.originalUrl()).isEqualTo(HttpUrl.of("https://example.com/updated"));
        assertThat(link.updatedAtUtc()).isEqualTo(updatedAtUtc);
        assertThat(link.version()).isEqualTo(4L);

        assertThat(link.applyUpdate(patch, false, updatedAtUtc.plusSeconds(1)).hasChanges()).isFalse();
        assertThat(link.version()).isEqualTo(4L);
    }

    @Test
    void applyUpdate_shouldRecordRelatedTagChangeWithoutFieldMutation() {
        ShortLink link = rehydratedLink(ShortLinkLifecycleState.ACTIVE, null, null, null, 3L);
        LocalDateTime updatedAtUtc = LocalDateTime.parse("2026-04-28T04:05:06");

        ShortLinkChangeSet changes = link.applyUpdate(emptyPatch(), true, updatedAtUtc);

        assertThat(changes.hasChanges()).isFalse();
        assertThat(link.version()).isEqualTo(4L);
        assertThat(link.updatedAtUtc()).isEqualTo(updatedAtUtc);
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

        ShortLink draft = rehydratedLink(ShortLinkLifecycleState.DRAFT, 2001L, 3001L, null, 8L);
        assertThatThrownBy(() -> draft.approveDestinationChange(approvedUrl, changedAtUtc))
                .isInstanceOf(ShortLinkDomainException.class)
                .hasMessageContaining("ACTIVE");
        assertThat(draft.version()).isEqualTo(8L);
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
