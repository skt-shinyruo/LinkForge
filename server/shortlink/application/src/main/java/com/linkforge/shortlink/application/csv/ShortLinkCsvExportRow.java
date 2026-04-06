package com.linkforge.shortlink.application.csv;

import java.time.Instant;
import java.util.List;

public record ShortLinkCsvExportRow(
        long id,
        String code,
        String originalUrl,
        String note,
        boolean enabled,
        Instant expiresAt,
        List<String> tags
) {
}
