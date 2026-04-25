package com.linkforge.governance.application.port;

import com.linkforge.governance.domain.ApprovalRequest;
import com.linkforge.governance.domain.SensitiveOperationType;

import java.time.LocalDateTime;

public interface ApprovalExecutionPort {

    boolean supports(SensitiveOperationType operationType);

    void execute(ApprovalRequest request, LocalDateTime executedAt);
}
