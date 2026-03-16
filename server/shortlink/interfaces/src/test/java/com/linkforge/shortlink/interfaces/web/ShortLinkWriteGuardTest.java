package com.linkforge.shortlink.interfaces.web;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortLinkWriteGuardTest {

    @Test
    void requireWriteEnabled_whenEnabled_shouldNoop() {
        new ShortLinkWriteGuard(true).requireWriteEnabled();
    }

    @Test
    void requireWriteEnabled_whenDisabled_shouldThrowServiceUnavailable() {
        assertThatThrownBy(() -> new ShortLinkWriteGuard(false).requireWriteEnabled())
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
                    assertThat(be.getMessage()).isEqualTo("维护中");
                });
    }

    @Test
    void serviceUnavailable_shouldMapToHttp503() {
        assertThat(ErrorCode.SERVICE_UNAVAILABLE.getHttpStatus()).isEqualTo(503);
    }
}

