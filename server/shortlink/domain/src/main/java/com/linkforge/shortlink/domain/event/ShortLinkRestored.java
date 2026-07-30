package com.linkforge.shortlink.domain.event;

/**
 * 已归档短链恢复为未归档状态时记录的领域事件。
 *
 * <p>未归档短链的重复恢复不会产生事件。该最小事件不携带恢复时间，应用层发布公开事件时使用恢复命令的 UTC 时间。</p>
 *
 * @param linkId 被恢复的短链 ID
 * @param tenantId 所属租户 ID
 * @param domainId 可选域名 ID
 * @param code 恢复时的短码
 */
public record ShortLinkRestored(
        long linkId,
        long tenantId,
        Long domainId,
        String code
) implements ShortLinkDomainEvent {
}
