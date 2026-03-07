package com.linkforge.foundation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private Jwt jwt = new Jwt();
    private ApiKey apiKey = new ApiKey();

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

    public static class Jwt {
        private String secret;
        private String issuer;
        private long ttlSeconds;
        private boolean cookieEnabled;
        private String cookieName;
        private boolean cookieSecure;
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

    public static class ApiKey {
        /**
         * 认证成功后写回 last_used_at 的最小间隔（秒）。
         *
         * <p>说明：用于降低 OpenAPI 高 QPS 场景下的 DB 写放大。</p>
         * <p>设为 0 表示不写回（彻底关闭 last_used_at 更新）。</p>
         */
        private long lastUsedUpdateIntervalSeconds = 300;

        public long getLastUsedUpdateIntervalSeconds() {
            return lastUsedUpdateIntervalSeconds;
        }

        public void setLastUsedUpdateIntervalSeconds(long lastUsedUpdateIntervalSeconds) {
            this.lastUsedUpdateIntervalSeconds = lastUsedUpdateIntervalSeconds;
        }
    }
}

