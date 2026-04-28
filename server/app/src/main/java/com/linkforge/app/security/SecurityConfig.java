package com.linkforge.app.security;

import com.linkforge.app.api.error.ApiErrorResponseWriter;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.security.AccountStatusVerifier;
import com.linkforge.foundation.security.ApiKeyAuthenticator;
import com.linkforge.foundation.security.JwtPrincipalVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

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

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler,
            JwtPrincipalVerifier jwtService,
            AccountStatusVerifier accountStatusService,
            ApiErrorResponseWriter errorResponseWriter,
            SecurityProperties securityProperties
    ) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(
                jwtService,
                accountStatusService,
                errorResponseWriter,
                securityProperties
        );
        http
                // In monolith mode we must not let security filters affect redirect endpoints (/r/**).
                // Keep the security chain strictly scoped to API routes.
                .securityMatcher("/api/**")
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                )
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // Cookie 模式进生产时必须具备 CSRF（双提交 cookie）。
        // Bearer header 等非 Cookie 认证路径不应被 CSRF 机制影响。
        if (securityProperties != null
                && securityProperties.getJwt() != null
                && securityProperties.getJwt().isCookieEnabled()) {
            CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
            repo.setCookiePath("/");
            // SPA（双提交 cookie）模式：使用“原始 token”而不是 XOR 掩码，保证 cookie 值可直接作为 header 发送
            CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
            http.csrf(csrf -> csrf
                    .csrfTokenRepository(repo)
                    .csrfTokenRequestHandler(requestHandler)
                    // 显式 header 认证（Bearer/API Key）不属于浏览器自动附带的 Cookie 场景
                    .ignoringRequestMatchers((HttpServletRequest req) -> {
                        if (req == null) {
                            return false;
                        }
                        String auth = req.getHeader("Authorization");
                        if (auth == null || auth.isBlank()) {
                            return false;
                        }
                        if (!auth.startsWith("Bearer ")) {
                            return false;
                        }
                        String token = auth.substring("Bearer ".length()).trim();
                        return !token.isBlank();
                    })
            );
        } else {
            http.csrf(csrf -> csrf.disable());
        }

        return http.build();
    }
}
