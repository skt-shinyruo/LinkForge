package com.linkforge.contract.analytics;

import java.util.Map;

/**
 * Redirect 侧采集到的访问上下文最小信息集。
 *
 * <p>该对象不依赖 Servlet API，所有字段都来自不可信请求。它不清洗 IP、UA、Referer 或 query 参数；生产者
 * 必须先按 allowlist 剔除 token、账号和其他敏感 query，Analytics 仍会再次做长度限制与归一化。标准维度
 * 归一化目前只读取 {@code utm_source}、{@code utm_medium} 与 {@code utm_campaign}。</p>
 *
 * <p>trackingParams 为 null 时变为不可变空 Map；非 null Map 会以 {@link Map#copyOf(Map)} 复制，因而 key/value
 * 不能为 null，后续修改原 Map 不会影响此记录。</p>
 *
 * @param ip 原始客户端 IP；允许为 {@code null}，标准 appender 只写其带盐指纹/哈希，不写原始 IP
 * @param userAgent 原始 User-Agent；允许为 {@code null}，下游会按配置截断和归一化
 * @param referer 原始 Referer；允许为 {@code null}，下游只提取规范化域名作为维度
 * @param acceptLanguage 原始 Accept-Language；允许为 {@code null}，下游只保留受限语言维度
 * @param trackingParams 已由生产者按 allowlist 过滤的查询参数；{@code null} 归一为不可变空 Map，非空 Map 的
 *                       key/value 不得为 {@code null}
 */
public record VisitContext(
        String ip,
        String userAgent,
        String referer,
        String acceptLanguage,
        Map<String, String> trackingParams
) {

    public VisitContext {
        trackingParams = trackingParams == null ? Map.of() : Map.copyOf(trackingParams);
    }
}
