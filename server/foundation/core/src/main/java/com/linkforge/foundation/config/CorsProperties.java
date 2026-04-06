package com.linkforge.foundation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * 允许的来源白名单（支持逗号分隔环境变量绑定）。
     *
     * <p>未配置时默认使用 allowOriginPatterns="*" 且 allowCredentials=false（仅推荐用于本地开发）。</p>
     */
    private List<String> allowedOrigins;

    /**
     * 是否允许携带凭证（cookie 模式需要开启）。开启后必须使用明确白名单，禁止 "*"。
     */
    private boolean allowCredentials;

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }
}

