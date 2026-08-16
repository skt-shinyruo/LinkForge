package com.linkforge.foundation.runtime.web;

import com.linkforge.foundation.config.CorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 管理 API 的 CORS 运行时装配。
 *
 * <p>未设置来源白名单时保留本地开发兼容行为：允许任意 origin pattern 但不允许凭据。启用凭据时必须使用
 * 明确来源，{@code *} 会在启动配置阶段抛出异常，避免浏览器拒绝该组合或误放大 Cookie 暴露面。</p>
 */
@Configuration
public class CorsConfig {

    /**
     * 构造供 Spring MVC 和安全过滤链共用的 CORS 规则。
     *
     * <p>响应暴露 request id 和游标分页元数据，供浏览器控制台关联日志并继续有界列表查询。</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> allowedOrigins = properties == null ? null : properties.getAllowedOrigins();
        boolean allowCredentials = properties != null && properties.isAllowCredentials();
        if (allowedOrigins != null) {
            allowedOrigins = allowedOrigins.stream()
                    .map(s -> s == null ? null : s.trim())
                    .filter(s -> s != null && !s.isBlank())
                    .toList();
        }

        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            cfg.setAllowedOriginPatterns(List.of("*"));
            cfg.setAllowCredentials(false);
        } else {
            if (allowCredentials && allowedOrigins.stream().anyMatch(o -> "*".equals(o))) {
                throw new IllegalStateException("CORS allowCredentials=true 时禁止 allowedOrigins 包含 \"*\"");
            }
            cfg.setAllowedOrigins(allowedOrigins);
            cfg.setAllowCredentials(allowCredentials);
        }

        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of(
                RequestIdFilter.HEADER_REQUEST_ID,
                CursorPaginationHeaders.HAS_MORE,
                CursorPaginationHeaders.NEXT_CURSOR
        ));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
