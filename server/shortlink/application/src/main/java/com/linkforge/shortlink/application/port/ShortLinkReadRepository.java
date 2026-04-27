package com.linkforge.shortlink.application.port;

import com.linkforge.contract.shortlink.ShortLinkReadPort;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ShortLinkReadRepository {

    Optional<ShortLinkReadPort.RedirectLinkView> findRedirectMetaByHostAndCode(String host, String code);

    Optional<ShortLinkReadPort.ShortLinkOwnership> findOwnership(long tenantId, long linkId);

    Map<Long, ShortLinkReadPort.ShortLinkSummary> listSummaries(long tenantId, List<Long> linkIds);

    List<Long> listLinkIdsByApplication(long tenantId, long applicationId);

    List<Long> listLinkIdsByDomain(long tenantId, long domainId);
}
