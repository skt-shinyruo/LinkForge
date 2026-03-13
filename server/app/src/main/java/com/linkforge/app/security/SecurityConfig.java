package com.linkforge.app.security;

import com.linkforge.foundation.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler,
            ApiCompositeAuthenticationFilter apiCompositeAuthenticationFilter,
            SecurityProperties securityProperties
    ) throws Exception {
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
                        .requestMatchers("/api/v1/open/**").authenticated()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(apiCompositeAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // Cookie 模式进生产时必须具备 CSRF（双提交 cookie）。
        // Bearer/OpenAPI Key 等非 Cookie 认证路径不应被 CSRF 机制影响。
        if (securityProperties != null
                && securityProperties.getJwt() != null
                && securityProperties.getJwt().isCookieEnabled()) {
            CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
            repo.setCookiePath("/");
            // SPA（双提交 cookie）模式：使用“原始 token”而不是 XOR 掩码，保证 cookie 值可直接作为 header 发送
            CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
            AntPathRequestMatcher openApiMatcher = new AntPathRequestMatcher("/api/v1/open/**");
            http.csrf(csrf -> csrf
                    .csrfTokenRepository(repo)
                    .csrfTokenRequestHandler(requestHandler)
                    // OpenAPI 客户端走 header 认证（X-API-Key），不依赖 Cookie，不需要 CSRF。
                    // 但当 OpenAPI 路由被 cookie/JWT 调用时，仍应受 CSRF 保护。
                    .ignoringRequestMatchers((HttpServletRequest req) -> {
                        if (req == null) {
                            return false;
                        }
                        if (!openApiMatcher.matches(req)) {
                            return false;
                        }
                        String apiKey = req.getHeader("X-API-Key");
                        return apiKey != null && !apiKey.isBlank();
                    })
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
