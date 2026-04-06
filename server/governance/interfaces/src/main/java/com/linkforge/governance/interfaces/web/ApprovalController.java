package com.linkforge.governance.interfaces.web;

import com.linkforge.contract.api.ApiResponse;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.security.AuthContext;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.web.RequestId;
import com.linkforge.governance.application.GovernanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final GovernanceService governanceService;

    public ApprovalController(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ApiResponse<List<GovernanceService.ApprovalRequestDto>> list() {
        long tenantId = AuthContext.requirePrincipal().getTenantId();
        return ApiResponse.ok(governanceService.listRequests(tenantId), RequestId.get());
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ApiResponse<GovernanceService.ApprovalRequestDto> approve(
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
                governanceService.approveRequest(principal.getTenantId(), requestId, req.reason(), actor, LocalDateTime.now(ZoneOffset.UTC)),
                RequestId.get()
        );
    }

    public record ApproveRequest(
            @Size(max = 512, message = "reason 过长")
            String reason
    ) {
    }
}
