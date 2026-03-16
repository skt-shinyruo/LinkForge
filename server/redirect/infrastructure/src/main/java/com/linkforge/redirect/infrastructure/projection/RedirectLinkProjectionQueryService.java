package com.linkforge.redirect.infrastructure.projection;

import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.redirect.application.projection.LinkMetaProjectionPort;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RedirectLinkProjectionQueryService implements LinkMetaProjectionPort {

    private final RedirectLinkProjectionMapper mapper;

    public RedirectLinkProjectionQueryService(RedirectLinkProjectionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<LinkMeta> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        RedirectLinkProjection row = mapper.findByCode(code.trim());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(toMeta(row));
    }

    static LinkMeta toMeta(RedirectLinkProjection row) {
        return new LinkMeta(
                row.getLinkId() == null ? 0L : row.getLinkId(),
                row.getTenantId() == null ? 0L : row.getTenantId(),
                row.getCode(),
                row.getOriginalUrl(),
                Boolean.TRUE.equals(row.getEnabled()),
                row.getExpiresAt(),
                row.getRedirectStatusCode(),
                Boolean.TRUE.equals(row.getPreviewEnabled()),
                row.getUnavailableLandingUrl(),
                row.getQueryForwardMode(),
                row.getQueryForwardAllowlist()
        );
    }
}

