package com.linkforge.shortlink.infrastructure.query;

import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.redirect.LinkMetaSourcePort;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkQueryMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuthoritativeLinkMetaSourceAdapter implements LinkMetaSourcePort {

    private final ShortLinkQueryMapper queryMapper;

    public AuthoritativeLinkMetaSourceAdapter(ShortLinkQueryMapper queryMapper) {
        this.queryMapper = queryMapper;
    }

    @Override
    public Optional<LinkMeta> findByCode(String code) {
        String normalized = normalizeNullable(code);
        if (normalized == null) {
            return Optional.empty();
        }
        ShortLinkEntity row = queryMapper.findActiveByCode(normalized);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new LinkMeta(
                row.getId() == null ? 0L : row.getId(),
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
        ));
    }

    private static String normalizeNullable(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
