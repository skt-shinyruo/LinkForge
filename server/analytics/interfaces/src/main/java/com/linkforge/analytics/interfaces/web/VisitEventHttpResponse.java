package com.linkforge.analytics.interfaces.web;

import java.time.LocalDateTime;

/**
 * 已授权访问明细的 HTTP 响应。
 *
 * <p>该记录只由管理员明细查询返回，字段来自采集与入库时的规范化结果。它是排障和分析
 * 数据，不是身份认证输入：{@code ipHash} 是假名化关联标识而非明文 IP，不能据此确认用户
 * 身份。截断后的原始 User-Agent、Referer 域名和 UTM 仍可能含有可关联或敏感信息，调用方不得把整个
 * 响应写入普通日志、错误报告或无访问控制的前端状态。
 *
 * <p>{@code occurredAt} 为按 UTC 解释的无时区时间。历史记录或缺失采集字段可为
 * {@code null}；客户端应将其当作未知值，而不是补造默认数据。
 *
 * @param occurredAt 访问发生时间，按 UTC 解释
 * @param requestId 原始跳转请求关联 ID，可为 {@code null}
 * @param ipHash 用于受控排障关联的 IP 假名化哈希，可为 {@code null}
 * @param userAgentRaw 截断后的原始 User-Agent，可为 {@code null}
 * @param userAgentFamily 解析出的浏览器族，可为 {@code null}
 * @param osFamily 解析出的操作系统族，可为 {@code null}
 * @param deviceType 解析出的设备类型，可为 {@code null}
 * @param refererDomain 规范化后的 Referer 域名，可为 {@code null}
 * @param language 规范化后的语言标记，可为 {@code null}
 * @param utmSource UTM source，可为 {@code null}
 * @param utmMedium UTM medium，可为 {@code null}
 * @param utmCampaign UTM campaign，可为 {@code null}
 */
public record VisitEventHttpResponse(
        LocalDateTime occurredAt,
        String requestId,
        String ipHash,
        String userAgentRaw,
        String userAgentFamily,
        String osFamily,
        String deviceType,
        String refererDomain,
        String language,
        String utmSource,
        String utmMedium,
        String utmCampaign
) {
}
