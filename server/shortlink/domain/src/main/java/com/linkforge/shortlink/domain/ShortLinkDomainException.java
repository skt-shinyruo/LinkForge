package com.linkforge.shortlink.domain;

/**
 * Domain-layer exception for validation and invariant violations.
 *
 * <p>Keep the domain module free of contract/error-code dependencies. Application layer should translate this
 * exception into a {@code BusinessException} with appropriate error code/message.</p>
 */
public class ShortLinkDomainException extends RuntimeException {

    public enum Reason {
        INVALID_TENANT_ID,
        INVALID_LINK_ID,
        INVALID_CODE,
        INVALID_URL,
        NOTE_TOO_LONG,
        INVALID_REDIRECT_STATUS_CODE,
        INVALID_QUERY_FORWARD_MODE,
        INVALID_QUERY_FORWARD_ALLOWLIST_ITEM,
        INVALID_QUERY_FORWARD_ALLOWLIST_TOO_LONG,
        UPDATE_NOT_ALLOWED_WHEN_ARCHIVED,
        DELETE_REQUIRES_ARCHIVE
    }

    private final Reason reason;
    private final String field;

    public ShortLinkDomainException(Reason reason, String field, String message) {
        super(message);
        this.reason = reason;
        this.field = field;
    }

    public ShortLinkDomainException(Reason reason, String message) {
        this(reason, null, message);
    }

    public Reason reason() {
        return reason;
    }

    public String field() {
        return field;
    }
}

