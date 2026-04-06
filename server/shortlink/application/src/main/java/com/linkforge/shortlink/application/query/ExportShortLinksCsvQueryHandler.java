package com.linkforge.shortlink.application.query;

import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.shortlink.application.csv.ShortLinkCsvExport;
import com.linkforge.shortlink.application.csv.ShortLinkCsvExportRow;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.OffsetPagingGuard;
import com.linkforge.shortlink.domain.ShortLink;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ExportShortLinksCsvQueryHandler {

    private static final long MAX_EXPORT_OFFSET = 100_000L;

    private final ShortLinkRepository shortLinkRepository;
    private final LinkTagRepository linkTagRepository;

    public ExportShortLinksCsvQueryHandler(
            ShortLinkRepository shortLinkRepository,
            LinkTagRepository linkTagRepository
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.linkTagRepository = linkTagRepository;
    }

    public ShortLinkCsvExport handle(long tenantId, ShortLinkSearchQuery query, PageQuery pageQuery) {
        long offset = OffsetPagingGuard.requireOffsetWithin(pageQuery, MAX_EXPORT_OFFSET);
        ShortLinkSearchQuery effectiveQuery = query == null ? new ShortLinkSearchQuery(false, null, null, null, null) : query;
        List<ShortLink> links = shortLinkRepository.listSearch(tenantId, effectiveQuery, offset, pageQuery.size());
        Map<Long, List<String>> tags = TagMaps.loadTagsByLinkIds(linkTagRepository, links);
        List<ShortLinkCsvExportRow> rows = new ArrayList<>(links.size());
        for (ShortLink link : links) {
            rows.add(new ShortLinkCsvExportRow(
                    link.id(),
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

    private static Instant toInstant(LocalDateTime expiresAtUtc) {
        if (expiresAtUtc == null) {
            return null;
        }
        return expiresAtUtc.toInstant(ZoneOffset.UTC);
    }
}
