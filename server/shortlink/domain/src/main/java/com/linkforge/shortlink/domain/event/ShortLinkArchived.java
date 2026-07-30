package com.linkforge.shortlink.domain.event;

import java.time.LocalDateTime;

/**
 * 短链首次从未归档变为已归档时记录的领域事件。
 *
 * <p>重复归档不会覆盖原时间或产生重复事件。归档状态独立于发布阶段和 enabled 标记；时间使用 UTC 语义的
 * {@link LocalDateTime}。</p>
 *
 * @param linkId 被归档的短链 ID
 * @param tenantId 所属租户 ID
 * @param domainId 可选域名 ID
 * @param code 归档时的短码
 * @param archivedAtUtc 首次归档时间，业务语义为 UTC
 */
public record ShortLinkArchived(
        long linkId,
        long tenantId,
        Long domainId,
        String code,
        LocalDateTime archivedAtUtc
) implements ShortLinkDomainEvent {
}
