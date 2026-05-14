package com.linkforge.shortlink.application;

import com.linkforge.foundation.context.ApiKeyActor;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.shortlink.application.csv.ShortLinkCsvExport;
import com.linkforge.shortlink.application.csv.ShortLinkCsvImportRow;
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

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShortLinkApplicationService implements
        ShortLinkCreationUseCase,
        ShortLinkQueryUseCase,
        ShortLinkLifecycleUseCase,
        ShortLinkCsvUseCase,
        ShortLinkTagUseCase {

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
    private final ShortLinkActorScopeResolver actorScopeResolver;

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
            ExportShortLinksCsvQueryHandler exportCsvHandler,
            ShortLinkActorScopeResolver actorScopeResolver
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
        this.actorScopeResolver = actorScopeResolver;
    }

    @Override
    public LinkDto createForUser(UserActor actor, ScopedCreateLinkRequest request) {
        CreateLinkRequest scopedRequest = actorScopeResolver.resolveCreateForUser(actor, request);
        return create(actor.tenantId(), CreatedBy.user(actor.userId()), scopedRequest);
    }

    @Override
    public LinkDto createForApiKey(ApiKeyActor actor, ScopedCreateLinkRequest request) {
        CreateLinkRequest scopedRequest = actorScopeResolver.resolveCreateForApiKey(actor, request);
        return create(actor.tenantId(), CreatedBy.apiKey(actor.apiKeyId()), scopedRequest);
    }

    @Override
    public PageResult<LinkDto> browseForUser(UserActor actor, BrowseLinksRequest request) {
        return search(
                actor.tenantId(),
                ShortLinkUserAccess.scopeBrowse(actor, actorScopeResolver.resolveBrowseForUser(actor, request)),
                actorScopeResolver.pageQuery(request)
        );
    }

    @Override
    public PageResult<LinkDto> browseForApiKey(ApiKeyActor actor, BrowseLinksRequest request) {
        return search(
                actor.tenantId(),
                actorScopeResolver.resolveBrowseForApiKey(actor, request),
                actorScopeResolver.pageQuery(request)
        );
    }

    @Override
    public ImportResult importCsv(UserActor actor, List<ShortLinkCsvImportRow> rows) {
        return importCsv(actor.tenantId(), CreatedBy.user(actor.userId()), rows);
    }

    @Override
    public ImportResult importCsv(UserActor actor, ScopedImportCsvRequest request) {
        ShortLinkActorScopeResolver.ImportScope scope = actorScopeResolver.resolveImportForUser(actor, request);
        if (scope.applicationId() == null) {
            return importCsv(actor.tenantId(), CreatedBy.user(actor.userId()), scope.rows());
        }
        return importCsvHandler.handle(
                actor.tenantId(),
                CreatedBy.user(actor.userId()),
                scope.rows(),
                scope.applicationId(),
                scope.domainId()
        );
    }

    @Override
    public ShortLinkCsvExport exportCsvForUser(UserActor actor, BrowseLinksRequest request) {
        return exportCsv(
                actor.tenantId(),
                actorScopeResolver.resolveBrowseForUser(actor, request),
                actorScopeResolver.pageQuery(request)
        );
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
    public LinkDto detailForUser(UserActor actor, long linkId) {
        return detailHandler.handle(actor, linkId);
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
    public LinkDto update(long tenantId, long linkId, UpdateLinkRequest req, UserActor actor, LocalDateTime requestedAt) {
        return updateHandler.handle(tenantId, linkId, req, actor, requestedAt);
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
    public ImportResult importCsv(long tenantId, CreatedBy createdBy, List<ShortLinkCsvImportRow> rows) {
        return importCsvHandler.handle(tenantId, createdBy, rows);
    }

    @Override
    public ShortLinkCsvExport exportCsv(long tenantId, ShortLinkSearchQuery query, PageQuery pageQuery) {
        return exportCsvHandler.handle(tenantId, query, pageQuery);
    }

}
