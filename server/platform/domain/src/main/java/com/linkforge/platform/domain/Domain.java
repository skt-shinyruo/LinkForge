package com.linkforge.platform.domain;

import java.time.LocalDateTime;

/**
 * 平台域名的持久化领域快照。
 *
 * <p>共享域名属于租户，通常不绑定 {@code applicationId}，应用必须另有显式授权；专属域名必须绑定
 * 一个应用，且只能由该应用使用。{@code hostname} 应先经 {@link Hostname} 规范化。该 record 为了兼容
 * 仓储映射不在构造器内强制这些跨字段约束，创建服务与 {@link DomainAuthorizationPolicy} 分别负责
 * 写入和使用阶段的校验。</p>
 *
 * <p>{@code trustClass} 是目标信任分类元数据，不替代启用状态、租户隔离或应用授权检查。</p>
 *
 * @param id 域名 ID
 * @param tenantId 所属租户 ID
 * @param applicationId 专属域名绑定的应用 ID；共享域名通常为 {@code null}
 * @param hostname 已规范化的 ASCII 主机名
 * @param scope 域名共享范围
 * @param status 域名是否可参与授权判断
 * @param trustClass 域名目标的信任分类
 * @param createdAt 创建时间，首次写入前可为 {@code null}
 * @param updatedAt 最近更新时间，首次写入前可为 {@code null}
 */
public record Domain(
        long id,
        long tenantId,
        Long applicationId,
        String hostname,
        DomainScope scope,
        DomainStatus status,
        TargetTrustClass trustClass,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
