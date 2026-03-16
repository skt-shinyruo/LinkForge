package com.linkforge.shortlink.application.mapper;

import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.shortlink.application.ShortLinkService.LinkDto;
import com.linkforge.shortlink.domain.QueryForwardMode;
import com.linkforge.shortlink.domain.ShortLink;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShortLinkDtoMapper {

    private final CoreProperties coreProperties;

    public ShortLinkDtoMapper(CoreProperties coreProperties) {
        this.coreProperties = coreProperties;
    }

    public LinkDto toDto(ShortLink link, List<String> tags) {
        if (link == null) {
            return null;
        }
        return new LinkDto(
                link.id(),
                link.tenantId(),
                link.code().value(),
                buildShortUrl(link.code().value()),
                link.originalUrl().value(),
                link.note(),
                link.enabled(),
                link.expiresAtUtc(),
                link.archivedAtUtc(),
                link.redirectStatusCode(),
                link.previewEnabled(),
                link.unavailableLandingUrl() == null ? null : link.unavailableLandingUrl().value(),
                toModeString(link.queryForwardMode()),
                link.queryForwardAllowlist() == null ? List.of() : link.queryForwardAllowlist().values(),
                tags == null ? List.of() : tags,
                link.createdAtUtc()
        );
    }

    private String buildShortUrl(String code) {
        String base = coreProperties == null ? null : coreProperties.getBaseUrl();
        if (base == null) {
            base = "";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/r/" + code;
    }

    private static String toModeString(QueryForwardMode mode) {
        return mode == null ? null : mode.name();
    }
}

