package com.linkforge.shortlink.interfaces.web;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ShortLinkWriteGuard {

    private final boolean writeEnabled;

    public ShortLinkWriteGuard(@Value("${app.shortlink.write-enabled:true}") boolean writeEnabled) {
        this.writeEnabled = writeEnabled;
    }

    public void requireWriteEnabled() {
        if (writeEnabled) {
            return;
        }
        throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "维护中");
    }
}

