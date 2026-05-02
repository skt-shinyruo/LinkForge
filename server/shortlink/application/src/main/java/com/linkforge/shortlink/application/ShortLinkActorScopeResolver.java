package com.linkforge.shortlink.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.foundation.context.ApiKeyActor;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.foundation.security.StandardRoles;
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

    public CreateLinkRequest resolveCreateForUser(
            UserActor actor,
            ScopedCreateLinkRequest request
    ) {
        CreateLinkRequest createRequest = requireCreateRequest(request);
        Long pathApplicationId = request.pathApplicationId();
        if (pathApplicationId == null) {
            if (createRequest.applicationId() != null || createRequest.domainId() != null) {
                requireTenantAdmin(actor);
            }
            return createRequest;
        }
        requireTenantAdmin(actor);
        applicationScopePort.requireApplicationExists(actor.tenantId(), pathApplicationId);
        Long requestApplicationId = createRequest.applicationId();
        if (requestApplicationId != null && !requestApplicationId.equals(pathApplicationId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请求体中的 applicationId 与路径不一致");
        }
        return withApplicationId(createRequest, pathApplicationId);
    }

    public CreateLinkRequest resolveCreateForApiKey(
            ApiKeyActor actor,
            ScopedCreateLinkRequest request
    ) {
        CreateLinkRequest createRequest = requireCreateRequest(request);
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

    public ShortLinkSearchQuery resolveBrowseForUser(UserActor actor, BrowseLinksRequest request) {
        Long applicationId = resolveRequestedApplicationId(actor, request);
        return new ShortLinkSearchQuery(
                request.archived() != null && request.archived(),
                request.enabled(),
                request.keyword(),
                request.tag(),
                applicationId
        );
    }

    public ShortLinkSearchQuery resolveBrowseForApiKey(ApiKeyActor actor, BrowseLinksRequest request) {
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

    public PageQuery pageQuery(BrowseLinksRequest request) {
        if (request == null) {
            return PageQuery.of(0, 20, 100);
        }
        return PageQuery.of(request.page(), request.size(), request.maxPageSize());
    }

    public ImportScope resolveImportForUser(UserActor actor, ScopedImportCsvRequest request) {
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

    private Long resolveRequestedApplicationId(UserActor actor, BrowseLinksRequest request) {
        if (request.pathApplicationId() == null) {
            if (request.requestedApplicationId() != null) {
                requireTenantAdmin(actor);
            }
            return request.requestedApplicationId();
        }
        requireTenantAdmin(actor);
        if (request.requestedApplicationId() != null && !request.pathApplicationId().equals(request.requestedApplicationId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请求中的 applicationId 与路径不一致");
        }
        applicationScopePort.requireApplicationExists(actor.tenantId(), request.pathApplicationId());
        return request.pathApplicationId();
    }

    private static void requireTenantAdmin(UserActor actor) {
        if (actor == null || actor.roles() == null || !actor.roles().contains(StandardRoles.TENANT_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "应用级短链需要租户管理员权限");
        }
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

    private static CreateLinkRequest requireCreateRequest(
            ScopedCreateLinkRequest request
    ) {
        if (request == null || request.createRequest() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CreateLinkRequest 不能为空");
        }
        return request.createRequest();
    }

    private static CreateLinkRequest withApplicationId(
            CreateLinkRequest createRequest,
            long applicationId
    ) {
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

    public record ImportScope(
            List<ShortLinkCsvImportRow> rows,
            Long applicationId,
            Long domainId
    ) {
    }
}
