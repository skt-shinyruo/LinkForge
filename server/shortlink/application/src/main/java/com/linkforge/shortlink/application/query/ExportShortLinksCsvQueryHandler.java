package com.linkforge.shortlink.application.query;

import com.linkforge.contract.platform.DomainHostnameLookupPort;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.shortlink.application.csv.ShortLinkCsvExport;
import com.linkforge.shortlink.application.csv.ShortLinkCsvExportRow;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.OffsetPagingGuard;
import com.linkforge.shortlink.domain.ShortLink;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExportShortLinksCsvQueryHandler {

    private static final long MAX_EXPORT_OFFSET = 100_000L;

    private final ShortLinkRepository shortLinkRepository;
    private final LinkTagRepository linkTagRepository;
    private final DomainHostnameLookupPort domainHostnameLookupPort;

    @Autowired
    public ExportShortLinksCsvQueryHandler(
            ShortLinkRepository shortLinkRepository,
            LinkTagRepository linkTagRepository,
            DomainHostnameLookupPort domainHostnameLookupPort
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.linkTagRepository = linkTagRepository;
        this.domainHostnameLookupPort = domainHostnameLookupPort;
    }

    public ExportShortLinksCsvQueryHandler(
            ShortLinkRepository shortLinkRepository,
            LinkTagRepository linkTagRepository
    ) {
        this(shortLinkRepository, linkTagRepository, null);
    }

    public ShortLinkCsvExport handle(long tenantId, ShortLinkSearchQuery query, PageQuery pageQuery) {
        long offset = OffsetPagingGuard.requireOffsetWithin(pageQuery, MAX_EXPORT_OFFSET);
        ShortLinkSearchQuery effectiveQuery = query == null ? new ShortLinkSearchQuery(false, null, null, null, null) : query;
        List<ShortLink> links = shortLinkRepository.listSearch(tenantId, effectiveQuery, offset, pageQuery.size());
        Map<Long, List<String>> tags = TagMaps.loadTagsByLinkIds(linkTagRepository, links);
        Map<Long, String> hostnames = new HashMap<>();
        List<ShortLinkCsvExportRow> rows = new ArrayList<>(links.size());
        for (ShortLink link : links) {
            rows.add(new ShortLinkCsvExportRow(
                    link.id(),
                    link.applicationId(),
                    link.domainId(),
                    resolveHostname(tenantId, link.domainId(), hostnames),
                    link.code().value(),
                    link.originalUrl().value(),
                    link.note(),
                    link.enabled(),
                    toInstant(link.expiresAtUtc()),
                    tags.getOrDefault(link.id(), List.of())
            ));
        }
        return new ShortLinkCsvExport(rows);
    }

    private String resolveHostname(long tenantId, Long domainId, Map<Long, String> hostnames) {
        if (domainId == null || domainHostnameLookupPort == null) {
            return null;
        }
        if (hostnames.containsKey(domainId)) {
            return hostnames.get(domainId);
        }
        String hostname = domainHostnameLookupPort.findDomainHostname(tenantId, domainId).orElse(null);
        hostnames.put(domainId, hostname);
        return hostname;
    }

    private static Instant toInstant(LocalDateTime expiresAtUtc) {
        if (expiresAtUtc == null) {
            return null;
        }
        return expiresAtUtc.toInstant(ZoneOffset.UTC);
    }
}
