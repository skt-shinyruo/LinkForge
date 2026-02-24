package com.linkforge.iam.web;

import com.linkforge.iam.service.AuthService;
import com.linkforge.platform.api.ApiResponse;
import com.linkforge.platform.config.AppProperties;
import com.linkforge.platform.security.AuthPrincipal;
import com.linkforge.platform.web.RequestId;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AppProperties properties;

    public AuthController(AuthService authService, AppProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req, HttpServletResponse response) {
        AuthService.AuthResult r = authService.register(req.tenantName(), req.email(), req.password());
        setJwtCookieIfEnabled(response, r.token());
        return ApiResponse.ok(AuthResponse.from(r.token(), r.principal()), RequestId.get());
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletResponse response) {
        AuthService.AuthResult r = authService.login(req.email(), req.password());
        setJwtCookieIfEnabled(response, r.token());
        return ApiResponse.ok(AuthResponse.from(r.token(), r.principal()), RequestId.get());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        clearJwtCookieIfEnabled(response);
        return ApiResponse.ok(null, RequestId.get());
    }

    private void setJwtCookieIfEnabled(HttpServletResponse response, String token) {
        if (response == null) {
            return;
        }
        if (!properties.getSecurity().getJwt().isCookieEnabled()) {
            return;
        }
        String name = defaultIfBlank(properties.getSecurity().getJwt().getCookieName(), "lf_token");
        String sameSite = defaultIfBlank(properties.getSecurity().getJwt().getCookieSameSite(), "Lax");
        boolean secure = properties.getSecurity().getJwt().isCookieSecure();
        long maxAgeSeconds = Math.max(properties.getSecurity().getJwt().getTtlSeconds(), 0);

        ResponseCookie cookie = ResponseCookie.from(name, token == null ? "" : token)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite(sameSite)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearJwtCookieIfEnabled(HttpServletResponse response) {
        if (response == null) {
            return;
        }
        if (!properties.getSecurity().getJwt().isCookieEnabled()) {
            return;
        }
        String name = defaultIfBlank(properties.getSecurity().getJwt().getCookieName(), "lf_token");
        String sameSite = defaultIfBlank(properties.getSecurity().getJwt().getCookieSameSite(), "Lax");
        boolean secure = properties.getSecurity().getJwt().isCookieSecure();

        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite(sameSite)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String defaultIfBlank(String v, String dft) {
        if (v == null) {
            return dft;
        }
        String t = v.trim();
        return t.isBlank() ? dft : t;
    }

    public record RegisterRequest(
            @NotBlank(message = "租户名不能为空")
            @Size(max = 128, message = "租户名过长")
            String tenantName,
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 256, message = "邮箱过长")
            String email,
            @NotBlank(message = "密码不能为空")
            @Size(min = 8, max = 64, message = "密码长度需为 8-64")
            String password
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 256, message = "邮箱过长")
            String email,
            @NotBlank(message = "密码不能为空")
            @Size(min = 8, max = 64, message = "密码长度需为 8-64")
            String password
    ) {
    }

    public record AuthResponse(String token, UserDto user) {
        static AuthResponse from(String token, AuthPrincipal p) {
            return new AuthResponse(token, new UserDto(p.getUserId(), p.getTenantId(), p.getEmail(), p.getRoles()));
        }
    }

    public record UserDto(long id, long tenantId, String email, java.util.Set<String> roles) {
    }
}
