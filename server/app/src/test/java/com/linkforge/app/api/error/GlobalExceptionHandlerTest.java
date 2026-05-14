package com.linkforge.app.api.error;

import com.linkforge.contract.api.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void handleMissingRequestParameter_shouldReturnBadRequestInsteadOfInternalError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("from", "LocalDate");

        ResponseEntity<?> response = handler.handleMissingRequestParameter(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .extracting("code", "message")
                .containsExactly(ErrorCode.BAD_REQUEST.getCode(), "缺少必填参数: from");
    }
}
