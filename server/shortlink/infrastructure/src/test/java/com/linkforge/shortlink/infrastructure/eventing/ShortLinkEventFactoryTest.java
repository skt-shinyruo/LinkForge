package com.linkforge.shortlink.infrastructure.eventing;

import com.linkforge.contract.platform.DomainHostnameLookupPort;
import com.linkforge.contract.shortlink.ShortLinkPublicSnapshot;
import com.linkforge.contract.shortlink.event.ShortLinkArchivedV1;
import com.linkforge.contract.shortlink.event.ShortLinkCreatedV1;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShortLinkEventFactoryTest {

    @Test
    void created_shouldMapEntityToPublishedSnapshotContract() {
        DomainHostnameLookupPort hostnameLookupPort = mock(DomainHostnameLookupPort.class);
        when(hostnameLookupPort.findDomainHostname(1L, 3001L)).thenReturn(Optional.of("go.example.com"));
        ShortLinkEventFactory factory = new ShortLinkEventFactory(hostnameLookupPort);

        ShortLinkCreatedV1 event = factory.created(link(), Instant.parse("2026-04-28T08:00:00Z"), "evt-1");

        assertThat(event.eventId()).isEqualTo("evt-1");
        assertThat(event.occurredAtUtc()).isEqualTo(Instant.parse("2026-04-28T08:00:00Z"));
        assertThat(event.tenantId()).isEqualTo(1L);
        assertThat(event.linkId()).isEqualTo(101L);
        assertThat(event.code()).isEqualTo("abc123");

        ShortLinkPublicSnapshot snapshot = event.snapshot();
        assertThat(snapshot.tenantId()).isEqualTo(1L);
        assertThat(snapshot.linkId()).isEqualTo(101L);
        assertThat(snapshot.code()).isEqualTo("abc123");
        assertThat(snapshot.hostname()).isEqualTo("go.example.com");
        assertThat(snapshot.originalUrl()).isEqualTo("https://example.com/a");
        assertThat(snapshot.enabled()).isTrue();
        assertThat(snapshot.expiresAtUtc()).isEqualTo(Instant.parse("2026-05-01T10:15:30Z"));
        assertThat(snapshot.redirectStatusCode()).isEqualTo(302);
        assertThat(snapshot.previewEnabled()).isTrue();
        assertThat(snapshot.unavailableLandingUrl()).isEqualTo("https://example.com/unavailable");
        assertThat(snapshot.queryForwardMode()).isEqualTo("ALLOWLIST");
        assertThat(snapshot.queryForwardAllowlist()).containsExactly("utm_source", "utm_campaign");
        assertThat(snapshot.archivedAtUtc()).isNull();
        assertThat(snapshot.applicationId()).isEqualTo(2001L);
        assertThat(snapshot.domainId()).isEqualTo(3001L);
    }

    @Test
    void archived_shouldRequireArchivedAtAndPublishArchivedSnapshot() {
        DomainHostnameLookupPort hostnameLookupPort = mock(DomainHostnameLookupPort.class);
        when(hostnameLookupPort.findDomainHostname(1L, 3001L)).thenReturn(Optional.of("go.example.com"));
        ShortLinkEventFactory factory = new ShortLinkEventFactory(hostnameLookupPort);
        ShortLinkEntity link = link();
        link.setArchivedAt(LocalDateTime.parse("2026-04-28T09:30:00"));

        ShortLinkArchivedV1 event = factory.archived(link, Instant.parse("2026-04-28T09:31:00Z"), "evt-2");

        assertThat(event.snapshot().archivedAtUtc()).isEqualTo(Instant.parse("2026-04-28T09:30:00Z"));
        assertThat(event.snapshot().queryForwardAllowlist()).isEqualTo(List.of("utm_source", "utm_campaign"));
    }

    @Test
    void archived_shouldRejectSnapshotWithoutArchivedAt() {
        ShortLinkEventFactory factory = new ShortLinkEventFactory(mock(DomainHostnameLookupPort.class));

        assertThatThrownBy(() -> factory.archived(link(), Instant.parse("2026-04-28T09:31:00Z"), "evt-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("archived event requires non-null archivedAtUtc");
    }

    private static ShortLinkEntity link() {
        ShortLinkEntity link = new ShortLinkEntity();
        link.setId(101L);
        link.setTenantId(1L);
        link.setApplicationId(2001L);
        link.setDomainId(3001L);
        link.setCode("abc123");
        link.setOriginalUrl("https://example.com/a");
        link.setEnabled(true);
        link.setExpiresAt(LocalDateTime.parse("2026-05-01T10:15:30"));
        link.setRedirectStatusCode(302);
        link.setPreviewEnabled(true);
        link.setUnavailableLandingUrl("https://example.com/unavailable");
        link.setQueryForwardMode("ALLOWLIST");
        link.setQueryForwardAllowlist("utm_source, ,utm_campaign");
        return link;
    }
}
