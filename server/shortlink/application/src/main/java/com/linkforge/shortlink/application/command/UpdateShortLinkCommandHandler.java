package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.security.TenantGuard;
import com.linkforge.shortlink.application.ShortLinkService.LinkDto;
import com.linkforge.shortlink.application.ShortLinkService.UpdateLinkRequest;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.ShortLinkDomainExceptions;
import com.linkforge.shortlink.domain.HttpUrl;
import com.linkforge.shortlink.domain.QueryForwardAllowlist;
import com.linkforge.shortlink.domain.QueryForwardMode;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkDomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class UpdateShortLinkCommandHandler {

    private final ShortLinkRepository shortLinkRepository;
    private final SetLinkTagsCommandHandler setLinkTagsHandler;
    private final ShortLinkEventPublisher eventPublisher;
    private final LinkTagRepository linkTagRepository;
    private final ShortLinkDtoMapper dtoMapper;
    private final TenantGuard tenantGuard;
    private final Clock clock;

    public UpdateShortLinkCommandHandler(
            ShortLinkRepository shortLinkRepository,
            SetLinkTagsCommandHandler setLinkTagsHandler,
            ShortLinkEventPublisher eventPublisher,
            LinkTagRepository linkTagRepository,
            ShortLinkDtoMapper dtoMapper,
            TenantGuard tenantGuard,
            Clock clock
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.setLinkTagsHandler = setLinkTagsHandler;
        this.eventPublisher = eventPublisher;
        this.linkTagRepository = linkTagRepository;
        this.dtoMapper = dtoMapper;
        this.tenantGuard = tenantGuard;
        this.clock = clock;
    }

    @Transactional
    public LinkDto handle(long tenantId, long linkId, UpdateLinkRequest req) {
        tenantGuard.requireCurrentTenant(tenantId);
        if (req == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "UpdateLinkRequest 不能为空");
        }
        ShortLink link = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND));

        try {
            link.requireNotArchivedForUpdate();
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }

        if (req.originalUrl() != null) {
            try {
                link.changeOriginalUrl(HttpUrl.of(req.originalUrl()));
            } catch (ShortLinkDomainException ex) {
                throw ShortLinkDomainExceptions.translate(ex);
            }
        }
        if (req.note() != null) {
            try {
                link.changeNote(req.note());
            } catch (ShortLinkDomainException ex) {
                throw ShortLinkDomainExceptions.translate(ex);
            }
        }
        if (req.enabled() != null) {
            link.setEnabled(req.enabled());
        }
        if (Boolean.TRUE.equals(req.clearExpiresAt())) {
            link.clearExpiresAtUtc();
        } else if (req.expiresAt() != null) {
            LocalDateTime expiresAtUtc = req.expiresAt().atOffset(ZoneOffset.UTC).toLocalDateTime();
            link.setExpiresAtUtc(expiresAtUtc);
        }

        if (Boolean.TRUE.equals(req.clearRedirectStatusCode())) {
            if (req.redirectStatusCode() != null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "clearRedirectStatusCode=true 时不允许同时传 redirectStatusCode");
            }
            link.clearRedirectStatusCode();
        } else if (req.redirectStatusCode() != null) {
            try {
                link.setRedirectStatusCode(req.redirectStatusCode());
            } catch (ShortLinkDomainException ex) {
                throw ShortLinkDomainExceptions.translate(ex);
            }
        }

        if (req.previewEnabled() != null) {
            link.setPreviewEnabled(req.previewEnabled());
        }
        if (req.unavailableLandingUrl() != null) {
            // explicit empty string clears
            String normalized = normalizeNullable(req.unavailableLandingUrl());
            if (normalized == null) {
                link.clearUnavailableLandingUrl();
            } else {
                try {
                    link.setUnavailableLandingUrl(HttpUrl.of(normalized));
                } catch (ShortLinkDomainException ex) {
                    throw ShortLinkDomainExceptions.translate(ex);
                }
            }
        }

        if (Boolean.TRUE.equals(req.clearQueryForwardMode())) {
            if (req.queryForwardMode() != null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "clearQueryForwardMode=true 时不允许同时传 queryForwardMode");
            }
            link.clearQueryForwardMode();
        } else if (req.queryForwardMode() != null) {
            try {
                link.setQueryForwardMode(QueryForwardMode.parseNullable(req.queryForwardMode()));
            } catch (ShortLinkDomainException ex) {
                throw ShortLinkDomainExceptions.translate(ex);
            }
        }

        if (req.queryForwardAllowlist() != null) {
            try {
                link.setQueryForwardAllowlist(QueryForwardAllowlist.fromRaw(req.queryForwardAllowlist()));
            } catch (ShortLinkDomainException ex) {
                throw ShortLinkDomainExceptions.translate(ex);
            }
        }

        shortLinkRepository.update(link);

        if (req.tags() != null) {
            setLinkTagsHandler.handle(tenantId, linkId, req.tags());
        }

        eventPublisher.updated(link, clock.instant());

        List<String> tags = linkTagRepository.findTagNamesByLinkId(linkId);
        return dtoMapper.toDto(link, tags);
    }

    private static String normalizeNullable(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}
