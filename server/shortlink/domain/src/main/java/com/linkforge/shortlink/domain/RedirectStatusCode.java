package com.linkforge.shortlink.domain;

import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.INVALID_REDIRECT_STATUS_CODE;

public record RedirectStatusCode(int value) {

    public RedirectStatusCode {
        if (value != 301 && value != 302) {
            throw new ShortLinkDomainException(INVALID_REDIRECT_STATUS_CODE, "redirectStatusCode 仅支持 301/302");
        }
    }

    public static RedirectStatusCode of(int value) {
        return new RedirectStatusCode(value);
    }

    public static Integer normalize(Integer value) {
        return value == null ? null : of(value).value();
    }
}
