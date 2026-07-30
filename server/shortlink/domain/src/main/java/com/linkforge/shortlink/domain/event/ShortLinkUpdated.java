package com.linkforge.shortlink.domain.event;

import java.time.LocalDateTime;

/**
 * 一次完整短链编辑落库后记录的领域事件。
 *
 * <p>字段级修改不会逐项产生事件；应用层完成一次命令的全部修改后，通过 {@code ShortLink.markUpdated(...)} 记录
 * 单条事件。因此该事件表示最终状态需要重新发布/失效缓存，而不是某个字段的差异。时间使用 UTC 语义的
 * {@link LocalDateTime}。</p>
 *
 * @param linkId 被更新的短链 ID
 * @param tenantId 所属租户 ID
 * @param domainId 可选域名 ID
 * @param code 更新时的短码
 * @param updatedAtUtc 更新完成时间，业务语义为 UTC
 */
public record ShortLinkUpdated(
        long linkId,
        long tenantId,
        Long domainId,
        String code,
        LocalDateTime updatedAtUtc
) implements ShortLinkDomainEvent {
}
