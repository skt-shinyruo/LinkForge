package com.linkforge.shortlink.infrastructure.query;

import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.shortlink.application.ShortLinkReadService;
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
    public Optional<ShortLinkReadService.RedirectLinkMeta> findRedirectMetaByHostAndCode(String host, String code) {
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
    public Optional<ShortLinkReadService.LinkOwnership> findOwnership(long tenantId, long linkId) {
        return Optional.ofNullable(queryMapper.findByTenantIdAndId(tenantId, linkId))
                .map(row -> new ShortLinkReadService.LinkOwnership(row.getApplicationId(), row.getDomainId()));
    }

    @Override
    public Map<Long, ShortLinkReadService.LinkSummary> listSummaries(long tenantId, List<Long> linkIds) {
        if (linkIds == null || linkIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ShortLinkReadService.LinkSummary> summaries = new LinkedHashMap<>();
        for (ShortLinkEntity row : safeList(queryMapper.listByTenantIdAndIds(tenantId, linkIds))) {
            if (row == null || row.getId() == null) {
                continue;
            }
            summaries.put(row.getId(), new ShortLinkReadService.LinkSummary(
                    row.getId(),
                    row.getCode(),
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

    private static String normalizeNullable(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim();
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

    private static ShortLinkReadService.RedirectLinkMeta toRedirectLinkMeta(ShortLinkEntity row) {
        if (row == null) {
            return null;
        }
        return new ShortLinkReadService.RedirectLinkMeta(
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
