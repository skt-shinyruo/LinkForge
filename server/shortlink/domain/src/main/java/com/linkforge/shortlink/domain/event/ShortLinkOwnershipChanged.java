package com.linkforge.shortlink.domain.event;

import java.time.LocalDateTime;

/**
 * 短链 application/domain ownership 实际变化时记录的领域事件。
 *
 * <p>事件同时保留变化前后的 scope，使应用层可以失效两套路由身份。它不是新的跨上下文 wire shape；当前发布
 * 适配将其映射为包含最终完整快照的短链更新事件。</p>
 */
public record ShortLinkOwnershipChanged(
        long linkId,
        long tenantId,
        Long previousApplicationId,
        Long previousDomainId,
        Long applicationId,
        Long domainId,
        String code,
        LocalDateTime changedAtUtc
) implements ShortLinkDomainEvent {
}
