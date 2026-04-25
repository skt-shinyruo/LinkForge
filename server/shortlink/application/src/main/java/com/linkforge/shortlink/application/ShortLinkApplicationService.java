package com.linkforge.shortlink.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.platform.ApplicationScopePort;
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
    private final ApplicationScopePort applicationScopePort;

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
            ApplicationScopePort applicationScopePort
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
        this.applicationScopePort = applicationScopePort;
    }

    @Override
    public LinkDto createForUser(UserActor actor, ScopedCreateLinkRequest request) {
        CreateLinkRequest scopedRequest = withUserApplicationScope(actor, request);
        return create(actor.tenantId(), CreatedBy.user(actor.userId()), scopedRequest);
    }

    @Override
    public LinkDto createForApiKey(ApiKeyActor actor, ScopedCreateLinkRequest request) {
        CreateLinkRequest scopedRequest = withApiKeyApplicationScope(actor, request, true);
        return create(actor.tenantId(), CreatedBy.apiKey(actor.apiKeyId()), scopedRequest);
    }

    @Override
    public PageResult<LinkDto> browseForUser(UserActor actor, BrowseLinksRequest request) {
        return search(
                actor.tenantId(),
                buildSearchQuery(actor.tenantId(), request),
                buildPageQuery(request)
        );
    }

    @Override
    public PageResult<LinkDto> browseForApiKey(ApiKeyActor actor, BrowseLinksRequest request) {
        return search(
                actor.tenantId(),
                buildSearchQuery(actor, request),
                buildPageQuery(request)
        );
    }

    @Override
    public ImportResult importCsv(UserActor actor, List<ShortLinkCsvImportRow> rows) {
        return importCsv(actor.tenantId(), CreatedBy.user(actor.userId()), rows);
    }

    @Override
    public ImportResult importCsv(UserActor actor, ScopedImportCsvRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "导入请求不能为空");
        }
        Long pathApplicationId = request.pathApplicationId();
        if (pathApplicationId == null) {
            return importCsv(actor.tenantId(), CreatedBy.user(actor.userId()), request.rows());
        }
        applicationScopePort.requireApplicationExists(actor.tenantId(), pathApplicationId);
        if (request.domainId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "domainId 不能为空");
        }
        return importCsvHandler.handle(
                actor.tenantId(),
                CreatedBy.user(actor.userId()),
                request.rows(),
                pathApplicationId,
                request.domainId()
        );
    }

    @Override
    public ShortLinkCsvExport exportCsvForUser(UserActor actor, BrowseLinksRequest request) {
        return exportCsv(
                actor.tenantId(),
                buildSearchQuery(actor.tenantId(), request),
                buildPageQuery(request)
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

    private CreateLinkRequest withUserApplicationScope(UserActor actor, ScopedCreateLinkRequest request) {
        CreateLinkRequest createRequest = requireCreateRequest(request);
        Long pathApplicationId = request.pathApplicationId();
        if (pathApplicationId == null) {
            return createRequest;
        }
        applicationScopePort.requireApplicationExists(actor.tenantId(), pathApplicationId);
        Long requestApplicationId = createRequest.applicationId();
        if (requestApplicationId != null && !requestApplicationId.equals(pathApplicationId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请求体中的 applicationId 与路径不一致");
        }
        return withApplicationId(createRequest, pathApplicationId);
    }

    private CreateLinkRequest withApiKeyApplicationScope(
            ApiKeyActor actor,
            ScopedCreateLinkRequest request,
            boolean applicationRequired
    ) {
        CreateLinkRequest createRequest = requireCreateRequest(request);
        Long effectiveApplicationId = resolveAuthorizedApplicationId(
                actor,
                createRequest.applicationId(),
                request.pathApplicationId(),
                applicationRequired
        );
        if (request.pathApplicationId() != null) {
            applicationScopePort.requireApplicationExists(actor.tenantId(), effectiveApplicationId);
        }
        return effectiveApplicationId == null ? createRequest : withApplicationId(createRequest, effectiveApplicationId);
    }

    private ShortLinkSearchQuery buildSearchQuery(long tenantId, BrowseLinksRequest request) {
        Long applicationId = resolveRequestedApplicationId(tenantId, request);
        return new ShortLinkSearchQuery(
                request.archived() != null && request.archived(),
                request.enabled(),
                request.keyword(),
                request.tag(),
                applicationId
        );
    }

    private ShortLinkSearchQuery buildSearchQuery(ApiKeyActor actor, BrowseLinksRequest request) {
        Long effectiveApplicationId = resolveAuthorizedApplicationId(
                actor,
                request.requestedApplicationId(),
                request.pathApplicationId(),
                false
        );
        if (request.pathApplicationId() != null) {
            applicationScopePort.requireApplicationExists(actor.tenantId(), effectiveApplicationId);
        }
        return new ShortLinkSearchQuery(
                request.archived() != null && request.archived(),
                request.enabled(),
                request.keyword(),
                request.tag(),
                effectiveApplicationId
        );
    }

    private Long resolveRequestedApplicationId(long tenantId, BrowseLinksRequest request) {
        if (request.pathApplicationId() == null) {
            return request.requestedApplicationId();
        }
        if (request.requestedApplicationId() != null && !request.pathApplicationId().equals(request.requestedApplicationId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请求中的 applicationId 与路径不一致");
        }
        applicationScopePort.requireApplicationExists(tenantId, request.pathApplicationId());
        return request.pathApplicationId();
    }

    private static Long resolveAuthorizedApplicationId(
            ApiKeyActor actor,
            Long requestApplicationId,
            Long pathApplicationId,
            boolean applicationRequired
    ) {
        Long principalApplicationId = actor.applicationId();
        Long requestedApplicationId = pathApplicationId != null ? pathApplicationId : requestApplicationId;
        if (principalApplicationId != null) {
            if (requestedApplicationId != null && !principalApplicationId.equals(requestedApplicationId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "API Key 无权访问该应用");
            }
            return principalApplicationId;
        }
        if (requestedApplicationId == null && applicationRequired) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "applicationId 不能为空");
        }
        return requestedApplicationId;
    }

    private static CreateLinkRequest requireCreateRequest(ScopedCreateLinkRequest request) {
        if (request == null || request.createRequest() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CreateLinkRequest 不能为空");
        }
        return request.createRequest();
    }

    private static PageQuery buildPageQuery(BrowseLinksRequest request) {
        if (request == null) {
            return PageQuery.of(0, 20, 100);
        }
        return PageQuery.of(request.page(), request.size(), request.maxPageSize());
    }

    private static CreateLinkRequest withApplicationId(CreateLinkRequest createRequest, long applicationId) {
        return new CreateLinkRequest(
                createRequest.originalUrl(),
                createRequest.note(),
                createRequest.expiresAt(),
                createRequest.enabled(),
                createRequest.customCode(),
                createRequest.tags(),
                createRequest.redirectStatusCode(),
                createRequest.previewEnabled(),
                createRequest.unavailableLandingUrl(),
                createRequest.queryForwardMode(),
                createRequest.queryForwardAllowlist(),
                applicationId,
                createRequest.domainId(),
                createRequest.lifecycleState()
        );
    }
}
