package com.linkforge.governance.application;

import com.linkforge.foundation.context.UserActor;
import com.linkforge.governance.domain.SensitiveOperationType;

import java.time.LocalDateTime;

/**
 * 提交审批的应用层命令。
 *
 * <p>{@code beforeSnapshot}/{@code afterSnapshot} 是按 {@code operationType} 解释的不透明文本，允许为空。
 * 新版短链、统计和配额流程使用版本化 JSON，历史外部域名流程仍可能保存纯文本；Governance 在提交阶段
 * 只按原文持久化，只有权限判断或下游执行需要时才解析。{@code actor} 必须属于目标租户，
 * {@code requestedAt} 为空时由服务端时钟补齐。</p>
 */
public record SubmitApprovalRequest(
        SensitiveOperationType operationType,
        Long targetApplicationId,
        String beforeSnapshot,
        String afterSnapshot,
        UserActor actor,
        LocalDateTime requestedAt
) {
}
