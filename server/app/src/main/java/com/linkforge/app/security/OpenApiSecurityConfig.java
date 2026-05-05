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

@Configuration(proxyBeanMethods = false)
public class OpenApiSecurityConfig {

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
                // OpenAPI routes: API key only.
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
