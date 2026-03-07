package com.linkforge.shortlink.infrastructure.port;

import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.redirect.LinkMetaQueryPort;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import com.linkforge.shortlink.infrastructure.persistence.repo.ShortLinkRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ShortLinkMetaQueryAdapter implements LinkMetaQueryPort {

    private final ShortLinkRepository shortLinkRepository;

    public ShortLinkMetaQueryAdapter(ShortLinkRepository shortLinkRepository) {
        this.shortLinkRepository = shortLinkRepository;
    }

    @Override
    public Optional<LinkMeta> findActiveByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return shortLinkRepository.findByCodeAndArchivedAtIsNull(code.trim())
                .map(ShortLinkMetaQueryAdapter::toMeta);
    }

    @Override
    public Optional<LinkMeta> findById(long tenantId, long linkId) {
        if (tenantId <= 0 || linkId <= 0) {
            return Optional.empty();
        }
        return shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .map(ShortLinkMetaQueryAdapter::toMeta);
    }

    private static LinkMeta toMeta(ShortLinkEntity e) {
        return new LinkMeta(
                e.getId(),
                e.getTenantId(),
                e.getCode(),
                e.getOriginalUrl(),
                Boolean.TRUE.equals(e.getEnabled()),
                e.getExpiresAt(),
                e.getRedirectStatusCode(),
                Boolean.TRUE.equals(e.getPreviewEnabled()),
                e.getUnavailableLandingUrl(),
                e.getQueryForwardMode(),
                e.getQueryForwardAllowlist()
        );
    }
}

