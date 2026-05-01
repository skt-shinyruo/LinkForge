package com.linkforge.shortlink.infrastructure.query;

import com.linkforge.contract.shortlink.ShortLinkReadPort;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.shortlink.application.port.ShortLinkReadRepository;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkQueryMapper;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MybatisShortLinkReadRepository implements ShortLinkReadRepository {

    private final ShortLinkQueryMapper queryMapper;
    private final CoreProperties coreProperties;

    public MybatisShortLinkReadRepository(ShortLinkQueryMapper queryMapper, CoreProperties coreProperties) {
        this.queryMapper = queryMapper;
        this.coreProperties = coreProperties;
    }

    @Override
    public Optional<ShortLinkReadPort.RedirectLinkView> findRedirectMetaByHostAndCode(String host, String code) {
        String normalizedCode = normalizeNullable(code);
        String normalizedHost = normalizeHost(host);
        if (normalizedCode == null) {
            return Optional.empty();
        }
        if (normalizedHost == null) {
            return Optional.ofNullable(toRedirectLinkMeta(queryMapper.findActiveByCode(normalizedCode)));
        }

        ShortLinkEntity row = queryMapper.findActiveByHostnameAndCode(normalizedHost, normalizedCode);
        if (row == null) {
            boolean baseHost = isBaseHost(normalizedHost);
            if (baseHost) {
                row = queryMapper.findActiveByLegacyBaseHostAndCode(normalizedHost, normalizedCode);
            }
            if (row == null && baseHost) {
                row = queryMapper.findActiveUnscopedByCode(normalizedCode);
            }
        }
        if (row == null) {
            return Optional.empty();
        }
        if (row.getHostname() == null || row.getHostname().isBlank()) {
            row.setHostname(normalizedHost);
        }
        return Optional.of(toRedirectLinkMeta(row));
    }

    @Override
    public Optional<ShortLinkReadPort.ShortLinkOwnership> findOwnership(long tenantId, long linkId) {
        return Optional.ofNullable(queryMapper.findByTenantIdAndId(tenantId, linkId))
                .map(row -> new ShortLinkReadPort.ShortLinkOwnership(row.getApplicationId(), row.getDomainId()));
    }

    @Override
    public Map<Long, ShortLinkReadPort.ShortLinkSummary> listSummaries(long tenantId, List<Long> linkIds) {
        if (linkIds == null || linkIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ShortLinkReadPort.ShortLinkSummary> summaries = new LinkedHashMap<>();
        for (ShortLinkEntity row : safeList(queryMapper.listByTenantIdAndIds(tenantId, linkIds))) {
            if (row == null || row.getId() == null) {
                continue;
            }
            summaries.put(row.getId(), new ShortLinkReadPort.ShortLinkSummary(
                    row.getId(),
                    row.getCode(),
                    buildShortUrl(row),
                    row.getOriginalUrl(),
                    false
            ));
        }
        return Map.copyOf(summaries);
    }

    @Override
    public List<Long> listLinkIdsByApplication(long tenantId, long applicationId) {
        return List.copyOf(safeList(queryMapper.listIdsByTenantIdAndApplicationId(tenantId, applicationId)));
    }

    @Override
    public List<Long> listLinkIdsByDomain(long tenantId, long domainId) {
        return List.copyOf(safeList(queryMapper.listIdsByTenantIdAndDomainId(tenantId, domainId)));
    }

    private boolean isBaseHost(String host) {
        String baseHost = resolveBaseHost();
        return baseHost != null && baseHost.equalsIgnoreCase(host);
    }

    private String buildShortUrl(ShortLinkEntity row) {
        if (row == null || row.getCode() == null || row.getCode().isBlank()) {
            return null;
        }
        return appendRedirectPath(shortUrlBase(row), row.getCode());
    }

    private String shortUrlBase(ShortLinkEntity row) {
        String hostname = trimToNull(row.getHostname());
        Long domainId = row.getDomainId();
        if (domainId != null && domainId > 0L && hostname != null) {
            return schemeForDomainUrl() + "://" + hostname;
        }
        return configuredBaseUrl();
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

    private String configuredBaseUrl() {
        String base = coreProperties == null ? null : coreProperties.getBaseUrl();
        if (base == null) {
            base = "";
        }
        return trimTrailingSlash(base);
    }

    private String schemeForDomainUrl() {
        String base = coreProperties == null ? null : coreProperties.getBaseUrl();
        if (base != null && !base.isBlank()) {
            try {
                String scheme = URI.create(base.trim()).getScheme();
                if (scheme != null && !scheme.isBlank()) {
                    return scheme.toLowerCase();
                }
            } catch (Exception ignored) {
                // fall through to the public default
            }
        }
        return "https";
    }

    private static String appendRedirectPath(String base, String code) {
        return trimTrailingSlash(base) + "/r/" + code;
    }

    private static String trimTrailingSlash(String base) {
        if (base == null) {
            return "";
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static String normalizeNullable(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
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

    private static ShortLinkReadPort.RedirectLinkView toRedirectLinkMeta(ShortLinkEntity row) {
        if (row == null) {
            return null;
        }
        return new ShortLinkReadPort.RedirectLinkView(
                row.getTenantId() == null ? 0L : row.getTenantId(),
                row.getId() == null ? 0L : row.getId(),
                row.getCode(),
                normalizeHost(row.getHostname()),
                row.getOriginalUrl(),
                Boolean.TRUE.equals(row.getEnabled()),
                toInstant(row.getExpiresAt()),
                row.getRedirectStatusCode(),
                Boolean.TRUE.equals(row.getPreviewEnabled()),
                row.getUnavailableLandingUrl(),
                row.getQueryForwardMode(),
                row.getQueryForwardAllowlist(),
                row.getApplicationId(),
                row.getDomainId(),
                row.getLifecycleState()
        );
    }

    private static Instant toInstant(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.toInstant(ZoneOffset.UTC);
    }

    private static <T> List<T> safeList(List<T> rows) {
        return rows == null ? List.of() : rows;
    }
}
