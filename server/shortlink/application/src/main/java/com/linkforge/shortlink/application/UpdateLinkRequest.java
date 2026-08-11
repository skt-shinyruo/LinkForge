package com.linkforge.shortlink.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 短链部分更新输入，未提供的可空字段表示保持原值。
 *
 * <p>{@code clearExpiresAt}、{@code clearRedirectStatusCode} 和 {@code clearQueryForwardMode}
 * 为显式清空标志；标志为 {@code true} 时不能同时提供对应新值。{@code unavailableLandingUrl}
 * 没有独立 clear 标志，传空白字符串表示清空；非空 {@code queryForwardAllowlist} 会整体替换现有白名单，
 * 空列表因此表示清空。</p>
 *
 * <p>{@code expiresAt} 是绝对时间，进入领域层前按 UTC 转换。处理器会先把输入归一化为三态 patch 并与聚合快照比较；
 * 没有字段或标签实际变化时直接返回当前视图，不推进乐观锁版本、不发布事件，也不触发缓存失效。</p>
 */
public record UpdateLinkRequest(
        String originalUrl,
        String note,
        Instant expiresAt,
        Boolean clearExpiresAt,
        Boolean enabled,
        Set<String> tags,
        Integer redirectStatusCode,
        Boolean clearRedirectStatusCode,
        Boolean previewEnabled,
        String unavailableLandingUrl,
        String queryForwardMode,
        Boolean clearQueryForwardMode,
        List<String> queryForwardAllowlist,
        String lifecycleState
) {
}
