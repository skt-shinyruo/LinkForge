package com.linkforge.governance.interfaces.web;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.runtime.security.AuthContext;
import com.linkforge.foundation.runtime.web.CursorPaginationHeaders;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.governance.application.GovernanceService;
import com.linkforge.governance.application.GovernancePageResult;
import com.linkforge.governance.application.ApprovalRequestSummaryResult;
import com.linkforge.governance.domain.ApprovalStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.List;

/**
 * 审批查询与批准操作的 HTTP 边界。
 *
 * <p>租户范围完全取自已认证主体，不接受客户端覆盖。Spring Security 先执行角色门禁，应用服务
 * 再校验 actor 租户、禁止自审、操作级审批矩阵和状态 CAS；异常由统一错误映射转换为 HTTP
 * 响应，本控制器不把失败包装成成功。</p>
 */
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final GovernanceService governanceService;

    public ApprovalController(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    /**
     * 查询当前认证主体所属租户的一页审批摘要。
     *
     * <p>结果按创建时间倒序，且安全响应不暴露 before/after snapshot。</p>
     */
    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<List<ApprovalRequestHttpResponse>>> list(
            @RequestParam(name = "status", required = false) String status,
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
        GovernancePageResult<ApprovalRequestSummaryResult> page = governanceService.listRequests(
                principal.getTenantId(),
                actor,
                parseStatus(status),
                limit,
                cursor
        );
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(CursorPaginationHeaders.HAS_MORE, Boolean.toString(page.hasMore()));
        if (page.nextCursor() != null) {
            response.header(CursorPaginationHeaders.NEXT_CURSOR, page.nextCursor());
        }
        return response.body(ApiResponse.ok(
                page.items().stream().map(GovernanceHttpMapper::toApprovalResponse).toList(),
                RequestId.get()
        ));
    }

    private static ApprovalStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ApprovalStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审批状态无效");
        }
    }

    /**
     * 批准一个待审批请求，并在存在唯一执行器时同步执行敏感操作。
     *
     * <p>决策时间由服务端按 UTC 生成。并发输家、执行器失败或审计写入失败均不会返回成功；没有
     * 执行器支持的操作会停留在 {@code APPROVED}，而不是伪装成 {@code EXECUTED}。</p>
     */
    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ApiResponse<ApprovalRequestHttpResponse> approve(
            @PathVariable("requestId") long requestId,
            @Valid @RequestBody ApproveRequest req
    ) {
        AuthPrincipal principal = AuthContext.requirePrincipal();
        UserActor actor = new UserActor(
                principal.getTenantId(),
                principal.getUserId(),
                principal.getEmail(),
                principal.getRoles()
        );
        return ApiResponse.ok(
                GovernanceHttpMapper.toApprovalResponse(
                        governanceService.approveRequest(principal.getTenantId(), requestId, req.reason(), actor, LocalDateTime.now(ZoneOffset.UTC))
                ),
                RequestId.get()
        );
    }

    /** 批准请求体；理由可空，非空时最多 512 个字符。 */
    public record ApproveRequest(
            @Size(max = 512, message = "reason 过长")
            String reason
    ) {
    }
}
