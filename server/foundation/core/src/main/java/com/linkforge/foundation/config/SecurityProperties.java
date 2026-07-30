package com.linkforge.foundation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT、OpenAPI API Key 与注册开关的安全配置。
 *
 * <p>此对象包含敏感材料的引用而不是安全状态本身。密钥不得写入日志或 HTTP 响应；配置更新不会自动撤销
 * 已签发 JWT，撤销仍依赖 Accounts 的状态和 {@code tokenVersion} 校验。</p>
 */
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private Jwt jwt = new Jwt();
    private ApiKey apiKey = new ApiKey();
    private boolean registrationEnabled;

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public ApiKey getApiKey() {
        return apiKey;
    }

    public void setApiKey(ApiKey apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public void setRegistrationEnabled(boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    /**
     * 管理 API JWT 的签发和 Cookie 传输配置。
     *
     * <p>{@code ttlSeconds} 的单位为秒。Cookie 模式开启后，浏览器自动携带令牌，因此 API 安全链会启用
     * 双提交 CSRF 保护；显式 Bearer header 请求不走该 CSRF 校验。</p>
     */
    public static class Jwt {

        /** 用于 JWT 签名/验签的对称密钥；必须由启动校验和密钥管理系统保证强度与保密性。 */
        private String secret;

        /** 签发者 claim，验签时必须与令牌一致。 */
        private String issuer;

        /** JWT 有效期，单位为秒。 */
        private long ttlSeconds;

        /** 是否允许从 HTTP Cookie 读取和写入 JWT。 */
        private boolean cookieEnabled;

        /** Cookie 名称；过滤器在空值时使用兼容默认名 {@code lf_token}。 */
        private String cookieName;

        /** Cookie {@code Secure} 属性；HTTPS 部署应设置为 {@code true}。 */
        private boolean cookieSecure;

        /** Cookie {@code SameSite} 属性；空值时过滤器使用 {@code Lax}。 */
        private String cookieSameSite;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public boolean isCookieEnabled() {
            return cookieEnabled;
        }

        public void setCookieEnabled(boolean cookieEnabled) {
            this.cookieEnabled = cookieEnabled;
        }

        public String getCookieName() {
            return cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }

        public String getCookieSameSite() {
            return cookieSameSite;
        }

        public void setCookieSameSite(String cookieSameSite) {
            this.cookieSameSite = cookieSameSite;
        }
    }

    /**
     * OpenAPI API Key 认证路径的性能配置。
     *
     * <p>API Key 原文只在创建时交付，之后鉴权始终依赖持久化哈希校验。缓存只可短路已禁用状态，不能把
     * active 缓存视为凭据授权事实。</p>
     */
    public static class ApiKey {
        /**
         * 认证成功后写回 last_used_at 的最小间隔（秒）。
         *
         * <p>说明：用于降低 OpenAPI 高 QPS 场景下的 DB 写放大。</p>
         * <p>设为 0 表示不写回（彻底关闭 last_used_at 更新）。</p>
         */
        private long lastUsedUpdateIntervalSeconds = 300;

        /**
         * OpenAPI API Key 鉴权缓存 TTL（秒）。
         *
         * <p>该缓存只保存 disabled 短路状态；active 或未知状态仍回源数据库并执行 BCrypt，
         * 因而缓存不能充当有效凭据的授权事实源。</p>
         * <p>设为 0 表示关闭 disabled 负缓存。</p>
         */
        private long authCacheTtlSeconds = 60;

        public long getLastUsedUpdateIntervalSeconds() {
            return lastUsedUpdateIntervalSeconds;
        }

        public void setLastUsedUpdateIntervalSeconds(long lastUsedUpdateIntervalSeconds) {
            this.lastUsedUpdateIntervalSeconds = lastUsedUpdateIntervalSeconds;
        }

        public long getAuthCacheTtlSeconds() {
            return authCacheTtlSeconds;
        }

        public void setAuthCacheTtlSeconds(long authCacheTtlSeconds) {
            this.authCacheTtlSeconds = authCacheTtlSeconds;
        }
    }
}
