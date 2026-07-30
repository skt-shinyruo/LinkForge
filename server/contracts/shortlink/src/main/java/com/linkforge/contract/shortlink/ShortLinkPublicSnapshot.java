package com.linkforge.contract.shortlink;

import java.time.Instant;
import java.util.List;

/**
 * 五种短链集成事件共享的 V1 公开快照。
 *
 * <p>record component 名就是 V1 JSON 字段名，不能在原版本中重命名、删除或改变含义。时间字段为 UTC
 * {@link Instant}；hostname/applicationId/domainId 对 legacy 链接可为空，hostname 也可能因事件时无法反查
 * 域名而缺失。{@code archivedAtUtc} 表示该快照中的归档事实，而不是 event 的发生时间。query override 字段
 * 为 null 时表示由 Redirect 全局策略决定；allowlist 的顺序是 payload 的稳定事实。</p>
 *
 * <p>record 不会防御性复制 {@code queryForwardAllowlist}。事件生产者必须在 append 前提供不再修改的列表，
 * 消费者也不能依赖该列表必为不可变。新增字段或事件版本时，消费者必须按 event type/version 选择兼容 DTO。</p>
 *
 * @param tenantId 所属租户 ID；应与外层事件 tenantId 一致
 * @param linkId 全局短链 ID；应与外层事件 linkId 一致
 * @param code 大小写敏感短码；应与外层事件 code 一致
 * @param hostname 规范化 host，legacy 或反查失败时可为空
 * @param originalUrl 事件时刻的目标 URL
 * @param enabled 事件时刻的管理开关
 * @param expiresAtUtc UTC 到期时间，null 表示未设置
 * @param redirectStatusCode 单链重定向状态覆盖，null 表示使用默认
 * @param previewEnabled 事件时刻的预览开关
 * @param unavailableLandingUrl 不可用落地页覆盖，null 表示使用默认
 * @param queryForwardMode 查询透传模式覆盖，null 表示使用默认
 * @param queryForwardAllowlist 保序的透传白名单快照，可为空；空列表表示已知但没有白名单项
 * @param archivedAtUtc 此快照的归档时间；归档事件必须提供，非归档快照通常为空
 * @param applicationId 所属应用，legacy 数据可为空
 * @param domainId 所属域名，legacy 数据可为空
 */
public record ShortLinkPublicSnapshot(
        long tenantId,
        long linkId,
        String code,
        String hostname,
        String originalUrl,
        boolean enabled,
        Instant expiresAtUtc,
        Integer redirectStatusCode,
        boolean previewEnabled,
        String unavailableLandingUrl,
        String queryForwardMode,
        List<String> queryForwardAllowlist,
        Instant archivedAtUtc,
        Long applicationId,
        Long domainId
) {
}
