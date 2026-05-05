package com.linkforge.analytics.interfaces.web;

public record TopLinkStatHttpResponse(
        long linkId,
        String code,
        String shortUrl,
        String originalUrl,
        long pv,
        long uv,
        boolean deleted
) {
}
