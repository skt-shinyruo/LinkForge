package com.linkforge.edge.web.error;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EdgeErrorResponse(
        int code,
        String message,
        String requestId
) {
}

