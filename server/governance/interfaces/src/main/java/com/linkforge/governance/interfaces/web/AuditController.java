package com.linkforge.governance.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.runtime.security.AuthContext;
import com.linkforge.foundation.runtime.web.CursorPaginationHeaders;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.governance.application.GovernanceService;
import com.linkforge.governance.application.GovernancePageResult;
import com.linkforge.governance.application.AuditLogSummaryResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * 当前租户审计日志的只读 HTTP 边界。
 *
 * <p>租户 ID 取自认证主体，客户端不能跨租户指定查询范围；只有租户管理员和平台管理员可访问。
 * 列表只返回有界摘要，不携带审批前后快照。</p>
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {

    private final GovernanceService governanceService;

    public AuditController(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    /** 查询当前租户的一页审计摘要，按创建时间和 ID 倒序。 */
    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditLogHttpResponse>>> list(
            @RequestParam(name = "actionType", required = false) String actionType,
            @RequestParam(name = "resourceType", required = false) String resourceType,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "cursor", required = false) String cursor
    ) {
        AuthPrincipal principal = AuthContext.requirePrincipal();
        UserActor actor = new UserActor(
                principal.getTenantId(),
                principal.getUserId(),
                principal.getEmail(),
                principal.getRoles()
        );
        GovernancePageResult<AuditLogSummaryResult> page = governanceService.listAuditLogs(
                principal.getTenantId(),
                actor,
                actionType,
                resourceType,
                limit,
                cursor
        );
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(CursorPaginationHeaders.HAS_MORE, Boolean.toString(page.hasMore()));
        if (page.nextCursor() != null) {
            response.header(CursorPaginationHeaders.NEXT_CURSOR, page.nextCursor());
        }
        return response.body(ApiResponse.ok(
                page.items().stream().map(GovernanceHttpMapper::toAuditLogResponse).toList(),
                RequestId.get()
        ));
    }
}
