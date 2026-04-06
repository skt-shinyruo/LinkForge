package com.linkforge.governance.application;

import com.linkforge.contract.governance.ApprovalRequestView;
import com.linkforge.contract.governance.SensitiveOperation;
import com.linkforge.governance.domain.ApprovalStatus;
import com.linkforge.governance.domain.SensitiveOperationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GovernanceApprovalSubmissionAdapterTest {

    @Test
    void submitRequest_shouldTranslateContractVocabularyToGovernanceService() {
        GovernanceService governanceService = mock(GovernanceService.class);
        GovernanceApprovalSubmissionAdapter adapter = new GovernanceApprovalSubmissionAdapter(governanceService);

        when(governanceService.submitRequest(
                1L,
                argThat(req -> req.operationType() == SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE
                        && req.targetApplicationId().equals(2001L)
                        && "before".equals(req.beforeSnapshot())
                        && "after".equals(req.afterSnapshot()))
        )).thenReturn(new GovernanceService.ApprovalRequestDto(
                501L,
                1L,
                SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE,
                2001L,
                7L,
                "reviewer@example.com",
                ApprovalStatus.PENDING_APPROVAL,
                null,
                null,
                null
        ));

        ApprovalRequestView actual = adapter.submitRequest(
                1L,
                SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE,
                2001L,
                "before",
                "after",
                7L,
                "reviewer@example.com",
                Set.of("TENANT_ADMIN"),
                LocalDateTime.parse("2026-04-01T00:00:00")
        );

        assertThat(actual).isEqualTo(new ApprovalRequestView(
                501L,
                1L,
                SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE,
                2001L,
                7L,
                "reviewer@example.com",
                "PENDING_APPROVAL",
                null,
                null,
                null
        ));
        verify(governanceService).submitRequest(
                1L,
                argThat(req -> req.operationType() == SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE
                        && req.targetApplicationId().equals(2001L)
                        && "before".equals(req.beforeSnapshot())
                        && "after".equals(req.afterSnapshot()))
        );
    }
}
