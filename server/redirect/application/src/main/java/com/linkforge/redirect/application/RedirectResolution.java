package com.linkforge.redirect.application;

import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.redirect.application.error.RedirectErrorCode;

public record RedirectResolution(
        Kind kind,
        String code,
        boolean htmlRequest,
        LinkMeta meta,
        UnavailableReason unavailableReason
) {

    public enum Kind {
        REDIRECT,
        PREVIEW,
        NOT_FOUND,
        UNAVAILABLE
    }

    public enum UnavailableReason {
        DISABLED(RedirectErrorCode.LINK_DISABLED),
        EXPIRED(RedirectErrorCode.LINK_EXPIRED),
        QUOTA_EXCEEDED(RedirectErrorCode.TOO_MANY_REQUESTS);

        private final RedirectErrorCode errorCode;

        UnavailableReason(RedirectErrorCode errorCode) {
            this.errorCode = errorCode;
        }

        public RedirectErrorCode toErrorCode() {
            return errorCode;
        }
    }

    public static RedirectResolution redirect(String code, boolean htmlRequest, LinkMeta meta) {
        return new RedirectResolution(Kind.REDIRECT, code, htmlRequest, meta, null);
    }

    public static RedirectResolution preview(String code, boolean htmlRequest, LinkMeta meta) {
        return new RedirectResolution(Kind.PREVIEW, code, htmlRequest, meta, null);
    }

    public static RedirectResolution notFound(String code, boolean htmlRequest) {
        return new RedirectResolution(Kind.NOT_FOUND, code, htmlRequest, null, null);
    }

    public static RedirectResolution unavailable(
            String code,
            boolean htmlRequest,
            LinkMeta meta,
            UnavailableReason unavailableReason
    ) {
        return new RedirectResolution(Kind.UNAVAILABLE, code, htmlRequest, meta, unavailableReason);
    }
}
