package com.linkforge.shortlink.application.port;

import com.linkforge.shortlink.application.ShortLinkReadService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ShortLinkReadRepository {

    Optional<ShortLinkReadService.RedirectLinkMeta> findRedirectMetaByHostAndCode(String host, String code);

    Optional<ShortLinkReadService.LinkOwnership> findOwnership(long tenantId, long linkId);

    Map<Long, ShortLinkReadService.LinkSummary> listSummaries(long tenantId, List<Long> linkIds);

    List<Long> listLinkIdsByApplication(long tenantId, long applicationId);

    List<Long> listLinkIdsByDomain(long tenantId, long domainId);
}
