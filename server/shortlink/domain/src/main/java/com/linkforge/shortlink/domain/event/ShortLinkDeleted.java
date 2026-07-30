package com.linkforge.shortlink.domain.event;

import java.time.LocalDateTime;

/**
 * 已归档短链通过删除前置校验后记录的领域事件。
 *
 * <p>该事件首先表达事务内的删除意图；仓储随后按聚合版本物理删除，任何失败都应回滚整个事务。事件本身不证明数据库行
 * 已提交删除或外部订阅者已经收到。时间使用 UTC 语义的 {@link LocalDateTime}。</p>
 *
 * @param linkId 被删除的短链 ID
 * @param tenantId 所属租户 ID
 * @param domainId 可选域名 ID
 * @param code 删除时的短码
 * @param deletedAtUtc 删除命令发生时间，业务语义为 UTC
 */
public record ShortLinkDeleted(
        long linkId,
        long tenantId,
        Long domainId,
        String code,
        LocalDateTime deletedAtUtc
) implements ShortLinkDomainEvent {
}
