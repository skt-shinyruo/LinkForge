package com.linkforge.accounts.interfaces.web;

import com.linkforge.accounts.application.AuthResult;
import com.linkforge.accounts.application.AuthService;
import com.linkforge.contract.api.ApiResponse;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.web.RequestId;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 管理端身份建立与退出的 HTTP 边界。
 *
 * <p>注册、登录、退出和 CSRF token 端点在安全链中允许匿名访问；这只表示无需预先认证，
 * Cookie 模式下的写请求仍受双提交 Cookie CSRF 校验保护。注册是否开放还由
 * {@code app.security.registration-enabled} 单独控制。</p>
 *
 * <p>Bearer 模式把 JWT 放在响应体中，由调用方自行保管；Cookie 模式把 JWT 写入 HttpOnly Cookie，
 * 并将响应体的 {@code token} 固定为 {@code null}，避免脚本同时获得认证凭据。Cookie 的 Secure、
 * SameSite 和寿命取自安全配置，CSRF token 不是认证 token。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityProperties securityProperties;

    public AuthController(AuthService authService, SecurityProperties securityProperties) {
        this.authService = authService;
        this.securityProperties = securityProperties;
    }

    /**
     * 创建租户及其初始用户并建立登录态。
     *
     * <p>成功响应遵循当前 JWT 承载模式；Cookie 模式不会在 JSON 中重复暴露 JWT。</p>
     */
    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req, HttpServletResponse response) {
        if (securityProperties == null || !securityProperties.isRegistrationEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "自助注册未开启");
        }
        AuthResult r = authService.register(req.tenantName(), req.email(), req.password());
        setJwtCookieIfEnabled(response, r.token());
        return ApiResponse.ok(AuthResponse.from(exposeTokenToBodyIfNeeded(r.token()), r.principal()), RequestId.get());
    }

    /**
     * 校验凭据并建立登录态。
     *
     * <p>该路由虽允许匿名访问，但 Cookie 模式下仍须携带有效 CSRF header。</p>
     */
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletResponse response) {
        AuthResult r = authService.login(req.email(), req.password());
        setJwtCookieIfEnabled(response, r.token());
        return ApiResponse.ok(AuthResponse.from(exposeTokenToBodyIfNeeded(r.token()), r.principal()), RequestId.get());
    }

    /**
     * 获取 Cookie 认证模式使用的双提交 CSRF token。
     *
     * <p>前端先调用此端点获取或刷新 {@code XSRF-TOKEN} Cookie，再按响应中的 {@code headerName}
     * 把 token 放入写请求 header。该值只证明请求脚本能够读取同源 CSRF Cookie，不代表用户身份，
     * 不能用作 Bearer token。</p>
     */
    @GetMapping("/csrf")
    public ApiResponse<CsrfResponse> csrf(CsrfToken token) {
        if (token == null) {
            return ApiResponse.ok(null, RequestId.get());
        }
        return ApiResponse.ok(new CsrfResponse(token.getHeaderName(), token.getToken()), RequestId.get());
    }

    /**
     * 退出当前登录态。
     *
     * <p>存在已认证主体时，应用层递增 token version，使该用户此前签发的 JWT 失效；无主体时仍清除
     * 浏览器认证 Cookie，因此客户端可安全重试。Cookie 模式下该写请求仍需要 CSRF 保护。</p>
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthPrincipal principal, HttpServletResponse response) {
        if (principal != null) {
            authService.logout(principal.getUserId());
        }
        clearJwtCookieIfEnabled(response);
        return ApiResponse.ok(null, RequestId.get());
    }

    private void setJwtCookieIfEnabled(HttpServletResponse response, String token) {
        if (response == null) {
            return;
        }
        if (securityProperties == null || !securityProperties.getJwt().isCookieEnabled()) {
            return;
        }
        String name = defaultIfBlank(securityProperties.getJwt().getCookieName(), "lf_token");
        String sameSite = defaultIfBlank(securityProperties.getJwt().getCookieSameSite(), "Lax");
        boolean secure = securityProperties.getJwt().isCookieSecure();
        long maxAgeSeconds = Math.max(securityProperties.getJwt().getTtlSeconds(), 0);

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
        if (securityProperties == null || !securityProperties.getJwt().isCookieEnabled()) {
            return;
        }
        String name = defaultIfBlank(securityProperties.getJwt().getCookieName(), "lf_token");
        String sameSite = defaultIfBlank(securityProperties.getJwt().getCookieSameSite(), "Lax");
        boolean secure = securityProperties.getJwt().isCookieSecure();

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

    private String exposeTokenToBodyIfNeeded(String token) {
        if (securityProperties != null
                && securityProperties.getJwt() != null
                && securityProperties.getJwt().isCookieEnabled()) {
            return null;
        }
        return token;
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

    public record CsrfResponse(String headerName, String token) {
    }
}
