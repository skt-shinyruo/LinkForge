package com.linkforge.shortlink.application.csv;

public record ShortLinkCsvImportRow(
        long rowNumber,
        String applicationId,
        String domainId,
        String hostname,
        String originalUrl,
        String code,
        String expiresAt,
        String note,
        String tags
) {
    public ShortLinkCsvImportRow(
            long rowNumber,
            String originalUrl,
            String code,
            String expiresAt,
            String note,
            String tags
    ) {
        this(rowNumber, null, null, null, originalUrl, code, expiresAt, note, tags);
    }
}
