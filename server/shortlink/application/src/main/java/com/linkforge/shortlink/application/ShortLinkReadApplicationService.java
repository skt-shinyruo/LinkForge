package com.linkforge.shortlink.application;

import com.linkforge.contract.shortlink.ShortLinkReadPort;
import com.linkforge.shortlink.application.port.ShortLinkReadRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ShortLinkReadApplicationService implements ShortLinkReadPort {

    private final ShortLinkReadRepository shortLinkReadRepository;

    public ShortLinkReadApplicationService(ShortLinkReadRepository shortLinkReadRepository) {
        this.shortLinkReadRepository = shortLinkReadRepository;
    }

    @Override
    public Optional<RedirectLinkView> findRedirectMetaByHostAndCode(String host, String code) {
        return shortLinkReadRepository.findRedirectMetaByHostAndCode(host, code);
    }

    @Override
    public Optional<ShortLinkOwnership> findOwnership(long tenantId, long linkId) {
        return shortLinkReadRepository.findOwnership(tenantId, linkId);
    }

    @Override
    public Map<Long, ShortLinkSummary> listSummaries(long tenantId, List<Long> linkIds) {
        return shortLinkReadRepository.listSummaries(tenantId, linkIds);
    }
}
