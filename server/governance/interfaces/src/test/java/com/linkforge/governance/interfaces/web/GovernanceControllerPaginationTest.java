package com.linkforge.governance.interfaces.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.runtime.web.CursorPaginationHeaders;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.governance.application.ApprovalRequestSummaryResult;
import com.linkforge.governance.application.AuditLogSummaryResult;
import com.linkforge.governance.application.GovernancePageResult;
import com.linkforge.governance.application.GovernanceService;
import com.linkforge.governance.domain.ApprovalStatus;
import com.linkforge.governance.domain.SensitiveOperationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

class GovernanceControllerPaginationTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.parse("2026-08-15T10:00:00");

    @BeforeEach
    void authenticate() {
        AuthPrincipal principal = new AuthPrincipal(7L, 1L, "admin@example.test", Set.of("TENANT_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "N/A", List.of())
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void approvalList_shouldKeepArrayDataAndPublishOpaqueCursorHeaders() {
        GovernanceService service = mock(GovernanceService.class);
        ApprovalController controller = new ApprovalController(service);
        ApprovalRequestSummaryResult item = new ApprovalRequestSummaryResult(
                101L, 1L, SensitiveOperationType.ANALYTICS_DETAIL_EXPORT, 9L,
                6L, "requester@example.test", ApprovalStatus.PENDING_APPROVAL,
                null, null, null, CREATED_AT
        );
        when(service.listRequests(eq(1L), any(UserActor.class), eq(ApprovalStatus.PENDING_APPROVAL), eq(2), isNull()))
                .thenReturn(new GovernancePageResult<>(List.of(item), true, "v1.opaque"));

        ResponseEntity<?> response = controller.list("pending_approval", 2, null);

        assertThat(response.getHeaders().getFirst(CursorPaginationHeaders.HAS_MORE)).isEqualTo("true");
        assertThat(response.getHeaders().getFirst(CursorPaginationHeaders.NEXT_CURSOR)).isEqualTo("v1.opaque");
        Object data = ((com.linkforge.contract.api.ApiResponse<?>) response.getBody()).getData();
        assertThat(data).isInstanceOf(List.class);
        verify(service).listRequests(eq(1L), any(UserActor.class), eq(ApprovalStatus.PENDING_APPROVAL), eq(2), isNull());
    }

    @Test
    void auditList_shouldUseSameHeadersAndNeverSerializeSnapshots() throws Exception {
        GovernanceService service = mock(GovernanceService.class);
        AuditController controller = new AuditController(service);
        AuditLogSummaryResult item = new AuditLogSummaryResult(
                201L, 1L, 7L, "admin@example.test", "APPROVE_REQUEST",
                "approval_request", "101", 101L, CREATED_AT
        );
        when(service.listAuditLogs(
                eq(1L), any(UserActor.class), eq("APPROVE_REQUEST"), eq("approval_request"), eq(2), isNull()
        ))
                .thenReturn(new GovernancePageResult<>(List.of(item), false, null));

        ResponseEntity<?> response = controller.list("APPROVE_REQUEST", "approval_request", 2, null);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response.getBody());

        assertThat(response.getHeaders().getFirst(CursorPaginationHeaders.HAS_MORE)).isEqualTo("false");
        assertThat(response.getHeaders().containsKey(CursorPaginationHeaders.NEXT_CURSOR)).isFalse();
        assertThat(json).contains("APPROVE_REQUEST").doesNotContain("beforeSnapshot", "afterSnapshot");
    }

    @Test
    void approvalList_shouldReturnStableParameterErrorForUnknownStatus() {
        ApprovalController controller = new ApprovalController(mock(GovernanceService.class));

        assertThatThrownBy(() -> controller.list("unknown", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审批状态无效");
    }
}
