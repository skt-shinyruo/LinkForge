package com.linkforge.shortlink.domain;

import java.net.URI;

import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.INVALID_URL;

/**
 * HTTP/HTTPS URL value object.
 *
 * <p>Constraints: non-blank, length <= 2048, absolute URI with scheme http/https and non-empty host.</p>
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

    public static HttpUrl of(String raw) {
        return new HttpUrl(raw);
    }
}

