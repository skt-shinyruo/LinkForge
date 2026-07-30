package com.linkforge.app.security;

import com.linkforge.app.api.error.ApiErrorResponseWriter;
import com.linkforge.foundation.security.AccountStatusVerifier;
import com.linkforge.foundation.security.ApiKeyAuthenticator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * OpenAPI API Key 专用安全过滤链。
 *
 * <p>该链优先匹配 {@code /api/v1/open/**}，只接受 {@code X-API-Key}，不回退到 JWT 或 Cookie，并关闭
 * CSRF，因为调用方使用显式请求头凭据。应用 scope 与租户状态在 API Key 认证后写入安全上下文。</p>
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiSecurityConfig {

    /** 组装优先级最高的 OpenAPI 无状态认证链。 */
    @Bean
    @Order(1)
    public SecurityFilterChain openApiSecurityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler,
            ApiKeyAuthenticator apiKeyService,
            AccountStatusVerifier accountStatusService,
            ApiErrorResponseWriter errorResponseWriter
    ) throws Exception {
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter = new ApiKeyAuthenticationFilter(
                apiKeyService,
                accountStatusService,
                errorResponseWriter
        );
        http
                // OpenAPI 路由只允许 API Key；JWT/Cookie 不可作为替代凭据。
                .securityMatcher("/api/v1/open/**")
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                )
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(reg -> reg
                        .anyRequest().authenticated()
                )
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
