package com.linkforge.shortlink.infrastructure.persistence.mapper;

import com.linkforge.shortlink.domain.HttpUrl;
import com.linkforge.shortlink.domain.QueryForwardAllowlist;
import com.linkforge.shortlink.domain.QueryForwardMode;
import com.linkforge.shortlink.domain.ShortCode;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkDomainException;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;

/**
 * Maps between MyBatis persistence entities and domain aggregates.
 *
 * <p>Infrastructure layer: safe to depend on both domain and persistence.</p>
 */
public final class ShortLinkEntityMapper {

    private ShortLinkEntityMapper() {
    }

    public static ShortLink toDomain(ShortLinkEntity e) {
        if (e == null) {
            return null;
        }
        try {
            return ShortLink.rehydrate(
                    safeLong(e.getId()),
                    safeLong(e.getTenantId()),
                    ShortCode.of(e.getCode()),
                    HttpUrl.of(e.getOriginalUrl()),
                    e.getNote(),
                    Boolean.TRUE.equals(e.getEnabled()),
                    e.getExpiresAt(),
                    e.getArchivedAt(),
                    e.getRedirectStatusCode(),
                    Boolean.TRUE.equals(e.getPreviewEnabled()),
                    e.getUnavailableLandingUrl() == null ? null : HttpUrl.of(e.getUnavailableLandingUrl()),
                    QueryForwardMode.parseNullable(e.getQueryForwardMode()),
                    QueryForwardAllowlist.parseSerialized(e.getQueryForwardAllowlist()),
                    e.getCreatedBy() == null ? 0L : e.getCreatedBy(),
                    e.getCreatedAt(),
                    e.getUpdatedAt()
            );
        } catch (ShortLinkDomainException ex) {
            throw new IllegalStateException("invalid shortlink row in persistence: id=" + e.getId() + ", code=" + e.getCode(), ex);
        }
    }

    public static ShortLinkEntity toEntity(ShortLink link) {
        if (link == null) {
            return null;
        }
        ShortLinkEntity e = new ShortLinkEntity();
        e.setId(link.id());
        e.setTenantId(link.tenantId());
        e.setCode(link.code().value());
        e.setOriginalUrl(link.originalUrl().value());
        e.setNote(link.note());
        e.setEnabled(link.enabled());
        e.setExpiresAt(link.expiresAtUtc());
        e.setArchivedAt(link.archivedAtUtc());
        e.setRedirectStatusCode(link.redirectStatusCode());
        e.setPreviewEnabled(link.previewEnabled());
        e.setUnavailableLandingUrl(link.unavailableLandingUrl() == null ? null : link.unavailableLandingUrl().value());
        e.setQueryForwardMode(link.queryForwardMode() == null ? null : link.queryForwardMode().name());
        e.setQueryForwardAllowlist(link.queryForwardAllowlist() == null ? null : link.queryForwardAllowlist().serializeOrNull());
        e.setCreatedBy(link.createdBy());
        e.setCreatedAt(link.createdAtUtc());
        e.setUpdatedAt(link.updatedAtUtc());
        return e;
    }

    private static long safeLong(Long v) {
        return v == null ? 0L : v;
    }
}

