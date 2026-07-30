package com.linkforge.shortlink.domain;

import java.net.URI;

import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.INVALID_URL;

/**
 * 短链目标地址使用的 HTTP(S) URL 值对象。
 *
 * <p>构造时去除首尾空白，要求长度不超过 2048，并且能够解析成带非空 host 的绝对 {@link URI}；scheme
 * 仅接受 {@code http} 或 {@code https}（大小写不敏感）。保留去空白后的原始文本，不做 host 大小写、默认端口、路径或
 * percent-encoding 的规范化。</p>
 *
 * <p>该值对象只保证结构合法，不执行 DNS/网络可达性检查，也不判断私网地址或业务风险；SSRF 与风险策略由调用链的
 * 专用安全端口负责。</p>
 */
public record HttpUrl(String value) {

    public HttpUrl {
        if (value == null) {
            throw new ShortLinkDomainException(INVALID_URL, "URL 不能为空");
        }
        value = value.trim();
        if (value.isBlank()) {
            throw new ShortLinkDomainException(INVALID_URL, "URL 不能为空");
        }
        if (value.length() > 2048) {
            throw new ShortLinkDomainException(INVALID_URL, "URL 过长");
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (Exception ex) {
            throw new ShortLinkDomainException(INVALID_URL, "URL 不合法");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new ShortLinkDomainException(INVALID_URL, "仅支持 http/https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ShortLinkDomainException(INVALID_URL, "URL 缺少 host");
        }
    }

    /**
     * 从外部字符串创建并执行结构校验。
     *
     * @param raw 原始 URL
     * @return 保留原始表示（仅去除首尾空白）的 URL
     */
    public static HttpUrl of(String raw) {
        return new HttpUrl(raw);
    }
}
