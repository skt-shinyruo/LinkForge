package com.linkforge.shortlink.application.mapper;

import com.linkforge.contract.platform.DomainHostnameLookupPort;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.shortlink.application.LinkDto;
import com.linkforge.shortlink.domain.QueryForwardMode;
import com.linkforge.shortlink.domain.ShortLink;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Component
public class ShortLinkDtoMapper {

    private final CoreProperties coreProperties;
    private final DomainHostnameLookupPort domainHostnameLookupPort;

    public ShortLinkDtoMapper(CoreProperties coreProperties, DomainHostnameLookupPort domainHostnameLookupPort) {
        this.coreProperties = coreProperties;
        this.domainHostnameLookupPort = domainHostnameLookupPort;
    }

    public LinkDto toDto(ShortLink link, List<String> tags) {
        if (link == null) {
            return null;
        }
        return new LinkDto(
                link.id(),
                link.tenantId(),
                link.applicationId(),
                link.domainId(),
                link.lifecycleState().name(),
                link.code().value(),
                buildShortUrl(link),
                link.originalUrl().value(),
                link.note(),
                link.enabled(),
                toInstantUtc(link.expiresAtUtc()),
                toInstantUtc(link.archivedAtUtc()),
                link.redirectStatusCode(),
                link.previewEnabled(),
                link.unavailableLandingUrl() == null ? null : link.unavailableLandingUrl().value(),
                toModeString(link.queryForwardMode()),
                link.queryForwardAllowlist() == null ? List.of() : link.queryForwardAllowlist().values(),
                tags == null ? List.of() : tags,
                toInstantUtc(link.createdAtUtc())
        );
    }

    private String buildShortUrl(ShortLink link) {
        String code = link.code().value();
        return domainBaseUrl(link)
                .map(base -> appendRedirectPath(base, code))
                .orElseGet(() -> appendRedirectPath(configuredBaseUrl(), code));
    }

    private Optional<String> domainBaseUrl(ShortLink link) {
        Long domainId = link.domainId();
        if (domainId == null || domainId <= 0 || domainHostnameLookupPort == null) {
            return Optional.empty();
        }
        return domainHostnameLookupPort.findDomainHostname(link.tenantId(), domainId)
                .map(String::trim)
                .filter(hostname -> !hostname.isBlank())
                .map(hostname -> schemeForDomainUrl() + "://" + hostname);
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

    private static String toModeString(QueryForwardMode mode) {
        return mode == null ? null : mode.name();
    }

    private static Instant toInstantUtc(LocalDateTime utcLocalDateTime) {
        return utcLocalDateTime == null ? null : utcLocalDateTime.toInstant(ZoneOffset.UTC);
    }
}
