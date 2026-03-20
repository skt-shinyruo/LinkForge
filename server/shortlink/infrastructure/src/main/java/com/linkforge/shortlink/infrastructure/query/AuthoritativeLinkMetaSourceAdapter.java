package com.linkforge.shortlink.infrastructure.query;

import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.redirect.LinkMetaSourcePort;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkQueryMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

@Component
public class AuthoritativeLinkMetaSourceAdapter implements LinkMetaSourcePort {

    private final ShortLinkQueryMapper queryMapper;
    private final CoreProperties coreProperties;

    public AuthoritativeLinkMetaSourceAdapter(ShortLinkQueryMapper queryMapper, CoreProperties coreProperties) {
        this.queryMapper = queryMapper;
        this.coreProperties = coreProperties;
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
        return Optional.of(toMeta(row));
    }

    @Override
    public Optional<LinkMeta> findByHostAndCode(String host, String code) {
        String normalizedCode = normalizeNullable(code);
        String normalizedHost = normalizeHost(host);
        if (normalizedCode == null) {
            return Optional.empty();
        }
        if (normalizedHost == null) {
            return findByCode(normalizedCode);
        }

        ShortLinkEntity row = queryMapper.findActiveByHostnameAndCode(normalizedHost, normalizedCode);
        if (row == null && isLegacyBaseHost(normalizedHost)) {
            row = queryMapper.findActiveByLegacyBaseHostAndCode(normalizedHost, normalizedCode);
            if (row == null) {
                row = queryMapper.findActiveByCode(normalizedCode);
            }
        }
        if (row == null) {
            return Optional.empty();
        }
        if (row.getHostname() == null || row.getHostname().isBlank()) {
            row.setHostname(normalizedHost);
        }
        return Optional.of(toMeta(row));
    }

    private static String normalizeNullable(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private boolean isLegacyBaseHost(String host) {
        String baseHost = resolveBaseHost();
        return baseHost != null && baseHost.equalsIgnoreCase(host);
    }

    private String resolveBaseHost() {
        String baseUrl = coreProperties == null ? null : coreProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(baseUrl.trim());
            return normalizeHost(uri.getHost());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        String normalized = host.trim().toLowerCase();
        if (normalized.isBlank()) {
            return null;
        }
        int colonIndex = normalized.indexOf(':');
        if (colonIndex > 0) {
            normalized = normalized.substring(0, colonIndex);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private static LinkMeta toMeta(ShortLinkEntity row) {
        return new LinkMeta(
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
                row.getQueryForwardAllowlist(),
                row.getHostname()
        );
    }
}
