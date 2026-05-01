package com.linkforge.shortlink.application.support;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.shortlink.domain.ShortLinkDomainException;

public final class ShortLinkDomainExceptions {

    private ShortLinkDomainExceptions() {
    }

    public static BusinessException translate(ShortLinkDomainException ex) {
        if (ex == null) {
            return new BusinessException(ErrorCode.BAD_REQUEST, "domain validation failed");
        }
        return switch (ex.reason()) {
            case INVALID_URL -> new BusinessException(ShortLinkErrorCode.INVALID_URL, ex.getMessage());
            case INVALID_CODE,
                 NOTE_TOO_LONG,
                 INVALID_REDIRECT_STATUS_CODE,
                 INVALID_TAG,
                 INVALID_QUERY_FORWARD_MODE,
                 INVALID_QUERY_FORWARD_ALLOWLIST_ITEM,
                 INVALID_QUERY_FORWARD_ALLOWLIST_TOO_LONG,
                 UPDATE_NOT_ALLOWED_WHEN_ARCHIVED,
                 DELETE_REQUIRES_ARCHIVE,
                 INVALID_TENANT_ID,
                 INVALID_LINK_ID -> new BusinessException(ErrorCode.BAD_REQUEST, ex.getMessage());
        };
    }
}
