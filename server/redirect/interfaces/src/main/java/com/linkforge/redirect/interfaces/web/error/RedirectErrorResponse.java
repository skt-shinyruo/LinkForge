package com.linkforge.redirect.interfaces.web.error;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RedirectErrorResponse(
        int code,
        String message,
        String requestId
) {
}
