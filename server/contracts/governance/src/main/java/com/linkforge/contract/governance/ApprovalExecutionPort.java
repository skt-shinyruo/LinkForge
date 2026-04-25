package com.linkforge.contract.governance;

import java.time.LocalDateTime;

public interface ApprovalExecutionPort {

    boolean supports(SensitiveOperation operation);

    void execute(ApprovalExecutionRequest request, LocalDateTime executedAt);
}
