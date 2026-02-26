package com.linkforge.platform.web;

import com.linkforge.platform.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> allowedOrigins = properties.getCors() == null ? null : properties.getCors().getAllowedOrigins();
        boolean allowCredentials = properties.getCors() != null && properties.getCors().isAllowCredentials();
        if (allowedOrigins != null) {
            allowedOrigins = allowedOrigins.stream()
                    .map(s -> s == null ? null : s.trim())
                    .filter(s -> s != null && !s.isBlank())
                    .toList();
        }

        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            // 默认放开本地开发；生产环境建议收敛到具体域名并配合网关/反代
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
        cfg.setExposedHeaders(List.of(RequestIdFilter.HEADER_REQUEST_ID));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
