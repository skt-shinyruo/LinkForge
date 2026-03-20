package com.linkforge.shortlink.application;

import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.shortlink.application.command.ArchiveShortLinkCommandHandler;
import com.linkforge.shortlink.application.command.CreateShortLinkCommandHandler;
import com.linkforge.shortlink.application.command.CreateTagCommandHandler;
import com.linkforge.shortlink.application.command.DeleteShortLinkCommandHandler;
import com.linkforge.shortlink.application.command.ImportShortLinksCsvCommandHandler;
import com.linkforge.shortlink.application.command.RestoreShortLinkCommandHandler;
import com.linkforge.shortlink.application.command.UpdateShortLinkCommandHandler;
import com.linkforge.shortlink.application.query.ExportShortLinksCsvQueryHandler;
import com.linkforge.shortlink.application.query.GetShortLinkDetailQueryHandler;
import com.linkforge.shortlink.application.query.ListTagsQueryHandler;
import com.linkforge.shortlink.application.query.SearchShortLinksQueryHandler;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

@Service
public class ShortLinkApplicationService implements ShortLinkService {

    private final CreateShortLinkCommandHandler createHandler;
    private final UpdateShortLinkCommandHandler updateHandler;
    private final ArchiveShortLinkCommandHandler archiveHandler;
    private final RestoreShortLinkCommandHandler restoreHandler;
    private final DeleteShortLinkCommandHandler deleteHandler;
    private final GetShortLinkDetailQueryHandler detailHandler;
    private final SearchShortLinksQueryHandler searchHandler;
    private final ListTagsQueryHandler listTagsHandler;
    private final CreateTagCommandHandler createTagHandler;
    private final ImportShortLinksCsvCommandHandler importCsvHandler;
    private final ExportShortLinksCsvQueryHandler exportCsvHandler;

    public ShortLinkApplicationService(
            CreateShortLinkCommandHandler createHandler,
            UpdateShortLinkCommandHandler updateHandler,
            ArchiveShortLinkCommandHandler archiveHandler,
            RestoreShortLinkCommandHandler restoreHandler,
            DeleteShortLinkCommandHandler deleteHandler,
            GetShortLinkDetailQueryHandler detailHandler,
            SearchShortLinksQueryHandler searchHandler,
            ListTagsQueryHandler listTagsHandler,
            CreateTagCommandHandler createTagHandler,
            ImportShortLinksCsvCommandHandler importCsvHandler,
            ExportShortLinksCsvQueryHandler exportCsvHandler
    ) {
        this.createHandler = createHandler;
        this.updateHandler = updateHandler;
        this.archiveHandler = archiveHandler;
        this.restoreHandler = restoreHandler;
        this.deleteHandler = deleteHandler;
        this.detailHandler = detailHandler;
        this.searchHandler = searchHandler;
        this.listTagsHandler = listTagsHandler;
        this.createTagHandler = createTagHandler;
        this.importCsvHandler = importCsvHandler;
        this.exportCsvHandler = exportCsvHandler;
    }

    @Override
    public LinkDto create(long tenantId, CreatedBy createdBy, CreateLinkRequest req) {
        return createHandler.handle(tenantId, createdBy, req);
    }

    @Override
    public PageResult<LinkDto> search(long tenantId, ShortLinkSearchQuery query, PageQuery pageQuery) {
        return searchHandler.handle(tenantId, query, pageQuery);
    }

    @Override
    public LinkDto detail(long tenantId, long linkId) {
        return detailHandler.handle(tenantId, linkId);
    }

    @Override
    public LinkDto archive(long tenantId, long linkId) {
        return archiveHandler.handle(tenantId, linkId);
    }

    @Override
    public LinkDto restore(long tenantId, long linkId) {
        return restoreHandler.handle(tenantId, linkId);
    }

    @Override
    public void delete(long tenantId, long linkId) {
        deleteHandler.handle(tenantId, linkId);
    }

    @Override
    public LinkDto update(long tenantId, long linkId, UpdateLinkRequest req) {
        return updateHandler.handle(tenantId, linkId, req);
    }

    @Override
    public List<TagDto> listTags(long tenantId) {
        return listTagsHandler.handle(tenantId);
    }

    @Override
    public TagDto createTag(long tenantId, String name) {
        return createTagHandler.handle(tenantId, name);
    }

    @Override
    public ImportResult importCsv(long tenantId, CreatedBy createdBy, InputStream inputStream) {
        return importCsvHandler.handle(tenantId, createdBy, inputStream);
    }

    @Override
    public void exportCsv(long tenantId, ShortLinkSearchQuery query, PageQuery pageQuery, OutputStream os) {
        exportCsvHandler.handle(tenantId, query, pageQuery, os);
    }
}
