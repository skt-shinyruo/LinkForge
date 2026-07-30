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

/**
 * 将认证主体携带的可信作用域与请求路径、请求体中的非可信作用域合并为短链命令或查询参数。
 *
 * <p>{@link UserActor} 和 {@link ApiKeyActor} 必须来自已认证的安全上下文，不能由客户端字段构造。
 * 用户请求应用级资源时必须具备 {@code TENANT_ADMIN}；API Key 必须绑定应用，且路径或请求体中的
 * {@code applicationId} 只能与主体绑定值一致。路径应用存在性通过 {@link ApplicationScopePort} 在当前租户内校验，
 * 从而防止客户端通过伪造 ID 扩大数据范围。</p>
 *
 * <p>该组件只解析权限作用域，不开启事务，也不替代接口层对管理操作的角色保护。尤其是 CSV 导入入口仍须由
 * 调用方执行租户管理员授权。</p>
 */
@Component
public class ShortLinkActorScopeResolver {

    private final ApplicationScopePort applicationScopePort;

    public ShortLinkActorScopeResolver(ApplicationScopePort applicationScopePort) {
        this.applicationScopePort = applicationScopePort;
    }

    /**
     * 解析用户创建作用域。
     *
     * <p>未指定应用时普通用户只能创建无应用短链；请求体一旦携带应用或域名即要求租户管理员。
     * 指定路径应用时还会校验应用存在，并拒绝路径与请求体应用不一致的请求。</p>
     *
     * @param actor 已认证用户主体
     * @param request 包含创建参数及可选路径应用的请求
     * @return 应用作用域已经固定的创建参数
     */
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

    /**
     * 解析 API Key 创建作用域。
     *
     * <p>有效应用只能取自 API Key 主体的绑定值；未绑定应用或请求值与绑定值不一致时拒绝访问。</p>
     *
     * @param actor 已认证 API Key 主体
     * @param request 包含创建参数及可选路径应用的请求
     * @return 注入 API Key 绑定应用后的创建参数
     */
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

    /**
     * 将用户浏览条件转换为租户内查询条件。
     *
     * <p>普通用户不得自行指定应用过滤条件；其创建者范围随后由
     * {@link ShortLinkUserAccess#scopeBrowse(UserActor, ShortLinkSearchQuery)} 进一步收窄。</p>
     *
     * @param actor 已认证用户主体
     * @param request 浏览条件，不能为 {@code null}
     * @return 已校验应用条件但尚未附加普通用户所有者条件的查询
     */
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

    /**
     * 将 API Key 浏览条件固定到其绑定应用。
     *
     * @param actor 已认证且必须绑定应用的 API Key 主体
     * @param request 浏览条件，不能为 {@code null}
     * @return 应用 ID 与主体绑定值一致的查询
     */
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

    /**
     * 构造受最大页大小约束的分页参数；空请求使用第 0 页、每页 20 条、最大 100 条。
     *
     * @param request 浏览请求，可为 {@code null}
     * @return 已执行页码与大小边界校验的分页参数
     */
    public PageQuery pageQuery(BrowseLinksRequest request) {
        if (request == null) {
            return PageQuery.of(0, 20, 100);
        }
        return PageQuery.of(request.page(), request.size(), request.maxPageSize());
    }

    /**
     * 解析用户 CSV 导入作用域。
     *
     * <p>应用路径存在时，应用必须属于当前租户且必须同时提供域名 ID；此方法不检查租户管理员角色，
     * 调用入口必须先完成该授权。未指定路径应用时保留每行自带的作用域。</p>
     *
     * @param actor 已认证用户主体
     * @param request 导入行及可选路径作用域
     * @return 批次级应用和域名覆盖值
     */
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
        throw new BusinessException(ErrorCode.FORBIDDEN, "API Key 未绑定应用");
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

    /**
     * CSV 批次的可信作用域解析结果；非空应用和域名会覆盖各行对应字段。
     *
     * @param rows 待导入行
     * @param applicationId 批次应用；{@code null} 表示使用行内值
     * @param domainId 批次域名；{@code null} 表示使用行内值
     */
    public record ImportScope(
            List<ShortLinkCsvImportRow> rows,
            Long applicationId,
            Long domainId
    ) {
    }
}
