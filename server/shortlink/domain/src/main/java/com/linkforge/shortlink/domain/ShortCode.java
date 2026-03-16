package com.linkforge.shortlink.domain;

import static com.linkforge.shortlink.domain.ShortLinkDomainException.Reason.INVALID_CODE;

/**
 * Short code value object.
 *
 * <p>Constraints (V1): length 6-32, ASCII alphanumeric, case-sensitive.</p>
 */
public record ShortCode(String value) {

    public ShortCode {
        if (value == null) {
            throw new ShortLinkDomainException(INVALID_CODE, "短码不能为空");
        }
        value = value.trim();
        if (value.isBlank()) {
            throw new ShortLinkDomainException(INVALID_CODE, "短码不能为空");
        }
        if (value.length() < 6 || value.length() > 32) {
            throw new ShortLinkDomainException(INVALID_CODE, "短码长度需为 6-32");
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean ok = (ch >= '0' && ch <= '9')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z');
            if (!ok) {
                throw new ShortLinkDomainException(INVALID_CODE, "短码仅允许 [0-9A-Za-z]");
            }
        }
    }

    public static ShortCode of(String raw) {
        return new ShortCode(raw);
    }
}

