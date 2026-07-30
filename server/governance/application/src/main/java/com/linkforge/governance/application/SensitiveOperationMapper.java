package com.linkforge.governance.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.governance.SensitiveOperation;
import com.linkforge.governance.domain.SensitiveOperationType;

/**
 * 在 Governance 内部操作枚举与跨上下文发布枚举之间执行显式映射。
 *
 * <p>显式穷举使新增敏感操作在编译期暴露缺失分支，并避免持久化枚举与发布契约因名称偶合而静默漂移。</p>
 */
final class SensitiveOperationMapper {

    private SensitiveOperationMapper() {
    }

    /**
     * 将领域操作转换为执行器识别的稳定契约值；领域数据缺少操作类型表示持久化状态损坏。
     */
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
