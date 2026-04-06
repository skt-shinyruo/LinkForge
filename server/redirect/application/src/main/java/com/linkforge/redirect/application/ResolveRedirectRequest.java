package com.linkforge.redirect.application;

public record ResolveRedirectRequest(
        String code,
        String host,
        boolean htmlRequest,
        boolean confirmed,
        RedirectVisitInput visitInput
) {
}
