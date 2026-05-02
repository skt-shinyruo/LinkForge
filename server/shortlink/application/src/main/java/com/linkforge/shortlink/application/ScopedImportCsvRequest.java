package com.linkforge.shortlink.application;

import com.linkforge.shortlink.application.csv.ShortLinkCsvImportRow;

import java.util.List;

public record ScopedImportCsvRequest(
        List<ShortLinkCsvImportRow> rows,
        Long pathApplicationId,
        Long domainId
) {
}
