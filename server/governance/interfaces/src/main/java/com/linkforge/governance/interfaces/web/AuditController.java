package com.linkforge.governance.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.runtime.security.AuthContext;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.governance.application.GovernanceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前租户审计日志的只读 HTTP 边界。
 *
 * <p>租户 ID 取自认证主体，客户端不能跨租户指定查询范围；只有租户管理员和平台管理员可访问。
 * 返回内容包含审批前后快照，调用方应按敏感数据处理。</p>
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {

    private final GovernanceService governanceService;

    public AuditController(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    /** 查询当前租户的全部审计记录，按创建时间和 ID 倒序；当前接口不分页。 */
    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ApiResponse<List<AuditLogHttpResponse>> list() {
        AuthPrincipal principal = AuthContext.requirePrincipal();
        UserActor actor = new UserActor(
                principal.getTenantId(),
                principal.getUserId(),
                principal.getEmail(),
                principal.getRoles()
        );
        return ApiResponse.ok(
                governanceService.listAuditLogs(principal.getTenantId(), actor).stream()
                        .map(GovernanceHttpMapper::toAuditLogResponse)
                        .toList(),
                RequestId.get()
        );
    }
}
