package com.linkforge.shortlink.infrastructure.eventing;

import com.linkforge.contract.shortlink.ShortLinkPublicSnapshot;
import com.linkforge.contract.shortlink.event.ShortLinkArchivedV1;
import com.linkforge.contract.shortlink.event.ShortLinkCreatedV1;
import com.linkforge.contract.shortlink.event.ShortLinkDeletedV1;
import com.linkforge.contract.shortlink.event.ShortLinkRestoredV1;
import com.linkforge.contract.shortlink.event.ShortLinkUpdatedV1;
import com.linkforge.contract.platform.DomainHostnameLookupPort;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
public class ShortLinkEventFactory {

    private final DomainHostnameLookupPort domainHostnameLookupPort;

    public ShortLinkEventFactory(DomainHostnameLookupPort domainHostnameLookupPort) {
        this.domainHostnameLookupPort = domainHostnameLookupPort;
    }

    public ShortLinkPublicSnapshot toSnapshot(ShortLinkEntity e, Instant archivedAtUtc) {
        return new ShortLinkPublicSnapshot(
                e.getTenantId(),
                e.getId(),
                e.getCode(),
                resolveHostname(e),
                e.getOriginalUrl(),
                Boolean.TRUE.equals(e.getEnabled()),
                toUtcInstant(e.getExpiresAt()),
                e.getRedirectStatusCode(),
                Boolean.TRUE.equals(e.getPreviewEnabled()),
                e.getUnavailableLandingUrl(),
                e.getQueryForwardMode(),
                splitAllowlist(e.getQueryForwardAllowlist()),
                archivedAtUtc,
                e.getApplicationId(),
                e.getDomainId()
        );
    }

    private String resolveHostname(ShortLinkEntity e) {
        if (e == null || e.getTenantId() == null || e.getTenantId() <= 0 || e.getDomainId() == null || e.getDomainId() <= 0) {
            return null;
        }
        return domainHostnameLookupPort.findDomainHostname(e.getTenantId(), e.getDomainId())
                .orElse(null);
    }

    public ShortLinkCreatedV1 created(ShortLinkEntity e, Instant occurredAtUtc, String eventId) {
        return new ShortLinkCreatedV1(
                eventId,
                occurredAtUtc,
                e.getTenantId(),
                e.getId(),
                e.getCode(),
                toSnapshot(e, null)
        );
    }

    public ShortLinkUpdatedV1 updated(ShortLinkEntity e, Instant occurredAtUtc, String eventId) {
        return new ShortLinkUpdatedV1(
                eventId,
                occurredAtUtc,
                e.getTenantId(),
                e.getId(),
                e.getCode(),
                toSnapshot(e, null)
        );
    }

    public ShortLinkArchivedV1 archived(ShortLinkEntity e, Instant occurredAtUtc, String eventId) {
        Instant archivedAtUtc = toUtcInstant(e.getArchivedAt());
        if (archivedAtUtc == null) {
            throw new IllegalArgumentException("archived event requires non-null archivedAtUtc");
        }
        return new ShortLinkArchivedV1(
                eventId,
                occurredAtUtc,
                e.getTenantId(),
                e.getId(),
                e.getCode(),
                toSnapshot(e, archivedAtUtc)
        );
    }

    public ShortLinkRestoredV1 restored(ShortLinkEntity e, Instant occurredAtUtc, String eventId) {
        return new ShortLinkRestoredV1(
                eventId,
                occurredAtUtc,
                e.getTenantId(),
                e.getId(),
                e.getCode(),
                toSnapshot(e, null)
        );
    }

    public ShortLinkDeletedV1 deleted(ShortLinkEntity e, Instant occurredAtUtc, String eventId) {
        return new ShortLinkDeletedV1(
                eventId,
                occurredAtUtc,
                e.getTenantId(),
                e.getId(),
                e.getCode(),
                toSnapshot(e, null)
        );
    }

    private static Instant toUtcInstant(LocalDateTime t) {
        if (t == null) {
            return null;
        }
        return t.toInstant(ZoneOffset.UTC);
    }

    private static List<String> splitAllowlist(String raw) {
        if (raw == null) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (trimmed.isBlank()) {
            return List.of();
        }
        String[] parts = trimmed.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p == null) {
                continue;
            }
            String t = p.trim();
            if (!t.isBlank()) {
                out.add(t);
            }
        }
        return out;
    }
}
