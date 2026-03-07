package com.linkforge.shortlink.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class UrlValidator {

    public void validateHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_URL, "originalUrl 不能为空");
        }
        if (url.length() > 2048) {
            throw new BusinessException(ErrorCode.INVALID_URL, "URL 过长");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_URL);
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new BusinessException(ErrorCode.INVALID_URL, "仅支持 http/https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_URL, "URL 缺少 host");
        }
    }
}
