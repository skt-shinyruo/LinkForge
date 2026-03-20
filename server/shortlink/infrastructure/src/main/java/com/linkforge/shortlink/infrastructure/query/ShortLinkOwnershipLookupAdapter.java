package com.linkforge.shortlink.infrastructure.query;

import com.linkforge.contract.shortlink.ShortLinkOwnershipLookupPort;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ShortLinkOwnershipLookupAdapter implements ShortLinkOwnershipLookupPort {

    private final ShortLinkRepository shortLinkRepository;

    public ShortLinkOwnershipLookupAdapter(ShortLinkRepository shortLinkRepository) {
        this.shortLinkRepository = shortLinkRepository;
    }

    @Override
    public Optional<ShortLinkOwnership> findByTenantIdAndId(long tenantId, long linkId) {
        return shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .map(link -> new ShortLinkOwnership(link.applicationId(), link.domainId()));
    }
}
