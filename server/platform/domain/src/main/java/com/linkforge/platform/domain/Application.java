package com.linkforge.platform.domain;

import java.time.LocalDateTime;

/**
 * 平台应用的持久化领域快照，也是域名授权和额度归属的主体。
 *
 * <p>{@code tenantId} 定义应用的租户边界，{@code applicationKey} 仅在同一租户内唯一；跨上下文调用
 * 必须同时携带租户与应用标识，不能只凭 {@code id} 推断访问权限。当前 record 用于承载数据库状态，
 * 不自行校验 key 长度、状态取值或时间戳；创建服务负责规范化与校验，数据库负责唯一性和最终约束。</p>
 *
 * <p>新建记录时 {@code createdAt}/{@code updatedAt} 可以为 {@code null}，由数据库默认值填充；从
 * 仓储读取的快照应包含实际时间。</p>
 *
 * @param id 平台应用 ID
 * @param tenantId 所属租户 ID
 * @param applicationKey 租户内稳定且唯一的应用键
 * @param displayName 面向管理端的展示名称
 * @param status 应用状态；当前启用值由 {@link PlatformDefaults#APPLICATION_STATUS_ACTIVE} 定义
 * @param createdAt 创建时间，写入前可为 {@code null}
 * @param updatedAt 最近更新时间，写入前可为 {@code null}
 */
public record Application(
        long id,
        long tenantId,
        String applicationKey,
        String displayName,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
