package com.linkforge.foundation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 管理 API 的跨域来源配置。
 *
 * <p>该类型只承载配置值；未配置白名单时 {@code CorsConfig} 会使用任意 origin pattern 且关闭凭据的开发
 * 兼容模式。它不是名为 {@code allow-origin-patterns} 的配置绑定对象，生产环境应显式设置
 * {@code allowed-origins} 并仅在 Cookie 会话模式需要时开启 {@code allow-credentials}。</p>
 */
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
