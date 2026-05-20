package com.linkforge.governance.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.governance.SensitiveOperation;
import com.linkforge.governance.domain.SensitiveOperationType;

final class SensitiveOperationMapper {

    private SensitiveOperationMapper() {
    }

    static SensitiveOperation toContractOperation(SensitiveOperationType operationType) {
        if (operationType == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "审批操作类型缺失");
        }
        return switch (operationType) {
            case APPLICATION_QUOTA_INCREASE -> SensitiveOperation.APPLICATION_QUOTA_INCREASE;
            case EXTERNAL_DOMAIN_BINDING -> SensitiveOperation.EXTERNAL_DOMAIN_BINDING;
            case PUBLIC_LINK_DESTINATION_CHANGE -> SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE;
            case ANALYTICS_DETAIL_EXPORT -> SensitiveOperation.ANALYTICS_DETAIL_EXPORT;
        };
    }
}
