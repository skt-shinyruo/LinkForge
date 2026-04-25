package com.linkforge.shortlink.application.csv;

import java.time.Instant;
import java.util.List;

public record ShortLinkCsvExportRow(
        long id,
        Long applicationId,
        Long domainId,
        String hostname,
        String code,
        String originalUrl,
        String note,
        boolean enabled,
        Instant expiresAt,
        List<String> tags
) {
    public ShortLinkCsvExportRow(
            long id,
            String code,
            String originalUrl,
            String note,
            boolean enabled,
            Instant expiresAt,
            List<String> tags
    ) {
        this(id, null, null, null, code, originalUrl, note, enabled, expiresAt, tags);
    }
}
