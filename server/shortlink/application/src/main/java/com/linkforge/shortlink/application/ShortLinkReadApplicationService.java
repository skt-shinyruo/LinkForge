package com.linkforge.shortlink.application;

import com.linkforge.shortlink.application.port.ShortLinkReadRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ShortLinkReadApplicationService implements ShortLinkReadService {

    private final ShortLinkReadRepository shortLinkReadRepository;

    public ShortLinkReadApplicationService(ShortLinkReadRepository shortLinkReadRepository) {
        this.shortLinkReadRepository = shortLinkReadRepository;
    }

    @Override
    public Optional<RedirectLinkMeta> findRedirectMetaByHostAndCode(String host, String code) {
        return shortLinkReadRepository.findRedirectMetaByHostAndCode(host, code);
    }

    @Override
    public Optional<LinkOwnership> findOwnership(long tenantId, long linkId) {
        return shortLinkReadRepository.findOwnership(tenantId, linkId);
    }

    @Override
    public Map<Long, LinkSummary> listSummaries(long tenantId, List<Long> linkIds) {
        return shortLinkReadRepository.listSummaries(tenantId, linkIds);
    }

    @Override
    public List<Long> listLinkIdsByApplication(long tenantId, long applicationId) {
        return shortLinkReadRepository.listLinkIdsByApplication(tenantId, applicationId);
    }

    @Override
    public List<Long> listLinkIdsByDomain(long tenantId, long domainId) {
        return shortLinkReadRepository.listLinkIdsByDomain(tenantId, domainId);
    }
}
