package com.linkforge.shortlink.application.query;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.foundation.runtime.security.TenantGuard;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.OffsetPagingGuard;
import com.linkforge.shortlink.domain.ShortLink;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Component
public class ExportShortLinksCsvQueryHandler {

    private static final long MAX_EXPORT_OFFSET = 100_000L;

    private final ShortLinkRepository shortLinkRepository;
    private final LinkTagRepository linkTagRepository;
    private final TenantGuard tenantGuard;

    public ExportShortLinksCsvQueryHandler(
            ShortLinkRepository shortLinkRepository,
            LinkTagRepository linkTagRepository,
            TenantGuard tenantGuard
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.linkTagRepository = linkTagRepository;
        this.tenantGuard = tenantGuard;
    }

    public void handle(long tenantId, ShortLinkSearchQuery query, PageQuery pageQuery, OutputStream os) {
        tenantGuard.requireCurrentTenant(tenantId);
        if (os == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "输出流不能为空");
        }
        exportCsv(tenantId, query, pageQuery, new OutputStreamWriter(os, StandardCharsets.UTF_8));
    }

    void exportCsv(long tenantId, ShortLinkSearchQuery query, PageQuery pageQuery, Writer writer) {
        tenantGuard.requireCurrentTenant(tenantId);
        long offset = OffsetPagingGuard.requireOffsetWithin(pageQuery, MAX_EXPORT_OFFSET);
        ShortLinkSearchQuery effectiveQuery = query == null ? new ShortLinkSearchQuery(false, null, null, null, null) : query;
        List<ShortLink> links = shortLinkRepository.listSearch(tenantId, effectiveQuery, offset, pageQuery.size());
        Map<Long, List<String>> tags = TagMaps.loadTagsByLinkIds(linkTagRepository, links);

        try (CSVPrinter printer = new CSVPrinter(
                writer,
                CSVFormat.DEFAULT.builder()
                        .setHeader("id", "code", "originalUrl", "note", "enabled", "expiresAt", "tags")
                        .build()
        )) {
            for (ShortLink e : links) {
                printer.printRecord(
                        e.id(),
                        e.code().value(),
                        e.originalUrl().value(),
                        e.note(),
                        e.enabled(),
                        formatExpiresAtUtc(e.expiresAtUtc()),
                        String.join(",", tags.getOrDefault(e.id(), List.of()))
                );
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导出失败");
        }
    }

    private static String formatExpiresAtUtc(LocalDateTime expiresAtUtc) {
        if (expiresAtUtc == null) {
            return "";
        }
        return expiresAtUtc.toInstant(ZoneOffset.UTC).toString();
    }
}
