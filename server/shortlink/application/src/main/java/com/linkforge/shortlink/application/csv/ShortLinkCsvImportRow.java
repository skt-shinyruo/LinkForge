package com.linkforge.shortlink.application.csv;

public record ShortLinkCsvImportRow(
        long rowNumber,
        String originalUrl,
        String code,
        String expiresAt,
        String note,
        String tags
) {
}
