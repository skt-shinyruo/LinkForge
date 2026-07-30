package com.linkforge.platform.domain;

import java.time.LocalDateTime;

/**
 * 应用创建时落库的平台策略快照。
 *
 * <p>这些字段在创建应用时一同落库，用于表达新建资源的默认策略，而不是对短链状态的强制覆盖。
 * 当前 Platform 只提供写入端口，Shortlink 创建链路尚未读取这张策略表，因此调用方仍需显式提供
 * 或使用自身默认值。该 record 不验证 HTTP 状态码范围或字段组合，写入服务应只持久化平台认可的值。</p>
 *
 * @param applicationId 策略所属应用 ID，同时也是持久化主键
 * @param defaultDomainScope 持久化的默认域名范围，当前尚未由 Shortlink 自动读取
 * @param defaultRedirectStatusCode 持久化的默认 HTTP 重定向状态码
 * @param previewEnabled 持久化的默认确认预览页开关
 * @param createdAt 创建时间，首次写入前可为 {@code null}
 * @param updatedAt 最近更新时间，首次写入前可为 {@code null}
 */
public record ApplicationPolicy(
        long applicationId,
        DomainScope defaultDomainScope,
        int defaultRedirectStatusCode,
        boolean previewEnabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
