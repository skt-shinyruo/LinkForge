package com.linkforge.shortlink.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.foundation.context.ApiKeyActor;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.shortlink.application.csv.ShortLinkCsvImportRow;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShortLinkActorScopeResolver {

    private final ApplicationScopePort applicationScopePort;

    public ShortLinkActorScopeResolver(ApplicationScopePort applicationScopePort) {
        this.applicationScopePort = applicationScopePort;
    }

    public ShortLinkService.CreateLinkRequest resolveCreateForUser(
            UserActor actor,
            ShortLinkService.ScopedCreateLinkRequest request
    ) {
        ShortLinkService.CreateLinkRequest createRequest = requireCreateRequest(request);
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

    public ShortLinkService.CreateLinkRequest resolveCreateForApiKey(
            ApiKeyActor actor,
            ShortLinkService.ScopedCreateLinkRequest request
    ) {
        ShortLinkService.CreateLinkRequest createRequest = requireCreateRequest(request);
        Long effectiveApplicationId = resolveAuthorizedApplicationId(
                actor,
                createRequest.applicationId(),
                request.pathApplicationId(),
                true
        );
        if (request.pathApplicationId() != null) {
            applicationScopePort.requireApplicationExists(actor.tenantId(), effectiveApplicationId);
        }
        return effectiveApplicationId == null ? createRequest : withApplicationId(createRequest, effectiveApplicationId);
    }

    public ShortLinkSearchQuery resolveBrowseForUser(long tenantId, ShortLinkService.BrowseLinksRequest request) {
        Long applicationId = resolveRequestedApplicationId(tenantId, request);
        return new ShortLinkSearchQuery(
                request.archived() != null && request.archived(),
                request.enabled(),
                request.keyword(),
                request.tag(),
                applicationId
        );
    }

    public ShortLinkSearchQuery resolveBrowseForApiKey(ApiKeyActor actor, ShortLinkService.BrowseLinksRequest request) {
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

    public PageQuery pageQuery(ShortLinkService.BrowseLinksRequest request) {
        if (request == null) {
            return PageQuery.of(0, 20, 100);
        }
        return PageQuery.of(request.page(), request.size(), request.maxPageSize());
    }

    public ImportScope resolveImportForUser(UserActor actor, ShortLinkService.ScopedImportCsvRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "导入请求不能为空");
        }
        Long pathApplicationId = request.pathApplicationId();
        if (pathApplicationId == null) {
            return new ImportScope(request.rows(), null, null);
        }
        applicationScopePort.requireApplicationExists(actor.tenantId(), pathApplicationId);
        if (request.domainId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "domainId 不能为空");
        }
        return new ImportScope(request.rows(), pathApplicationId, request.domainId());
    }

    private Long resolveRequestedApplicationId(long tenantId, ShortLinkService.BrowseLinksRequest request) {
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

    private static ShortLinkService.CreateLinkRequest requireCreateRequest(
            ShortLinkService.ScopedCreateLinkRequest request
    ) {
        if (request == null || request.createRequest() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CreateLinkRequest 不能为空");
        }
        return request.createRequest();
    }

    private static ShortLinkService.CreateLinkRequest withApplicationId(
            ShortLinkService.CreateLinkRequest createRequest,
            long applicationId
    ) {
        return new ShortLinkService.CreateLinkRequest(
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

    public record ImportScope(
            List<ShortLinkCsvImportRow> rows,
            Long applicationId,
            Long domainId
    ) {
    }
}
