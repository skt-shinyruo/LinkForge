package com.linkforge.shortlink.application;

import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.shortlink.application.csv.ShortLinkCsvExport;
import com.linkforge.shortlink.application.csv.ShortLinkCsvImportRow;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;

import java.util.List;

public interface ShortLinkCsvUseCase {

    ImportResult importCsv(UserActor actor, List<ShortLinkCsvImportRow> rows);

    ImportResult importCsv(UserActor actor, ScopedImportCsvRequest request);

    ImportResult importCsv(long tenantId, CreatedBy createdBy, List<ShortLinkCsvImportRow> rows);

    ShortLinkCsvExport exportCsvForUser(UserActor actor, BrowseLinksRequest request);

    ShortLinkCsvExport exportCsv(long tenantId, ShortLinkSearchQuery query, PageQuery pageQuery);
}
