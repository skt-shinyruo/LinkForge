package com.linkforge.contract.api;

/**
 * Error code abstraction for API responses.
 *
 * <p>Rationale: keep a tiny shared surface (code/message), while allowing each bounded context
 * to define its own error code enum without coupling everyone into a single global enum.</p>
 */
public interface AppErrorCode {

    int getCode();

    String getDefaultMessage();

    /**
     * Suggested HTTP status for this error code.
     *
     * <p>Defaults to 400 to keep existing behavior for business errors whose numeric code
     * is not an HTTP status-derived scheme.</p>
     */
    default int getHttpStatus() {
        return 400;
    }
}
