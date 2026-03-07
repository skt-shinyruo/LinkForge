package com.linkforge.foundation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.redirect")
public class RedirectProperties {

    private long cacheTtlSeconds;
    private int defaultStatusCode;

    /**
     * 短码不存在（404）的负缓存 TTL（秒）。
     *
     * <p>用于防止随机短码扫描导致缓存穿透，从而把 MySQL 回源打穿。</p>
     * <p>设为 0 表示关闭负缓存。</p>
     */
    private long notFoundCacheTtlSeconds = 60;

    /**
     * 短码不存在（404）时的全局落地页 URL（可选）。未配置则返回内置 HTML 页面。
     */
    private String notFoundLandingUrl;

    /**
     * 短码禁用/过期（410）时的全局落地页 URL（可选）。未配置则返回内置 HTML 页面。
     */
    private String goneLandingUrl;

    /**
     * Query 透传默认策略（OFF/ALLOWLIST/ALL）。未配置时视为 OFF。
     */
    private String queryForwardMode;

    /**
     * Query 透传默认白名单（支持前缀通配：utm_*）。
     */
    private List<String> queryForwardAllowlist;

    /**
     * 内部保留 query 参数（不会被透传到原始链接），用于预览确认等功能。
     */
    private List<String> queryForwardReservedParams;

    public long getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public int getDefaultStatusCode() {
        return defaultStatusCode;
    }

    public void setDefaultStatusCode(int defaultStatusCode) {
        this.defaultStatusCode = defaultStatusCode;
    }

    public long getNotFoundCacheTtlSeconds() {
        return notFoundCacheTtlSeconds;
    }

    public void setNotFoundCacheTtlSeconds(long notFoundCacheTtlSeconds) {
        this.notFoundCacheTtlSeconds = notFoundCacheTtlSeconds;
    }

    public String getNotFoundLandingUrl() {
        return notFoundLandingUrl;
    }

    public void setNotFoundLandingUrl(String notFoundLandingUrl) {
        this.notFoundLandingUrl = notFoundLandingUrl;
    }

    public String getGoneLandingUrl() {
        return goneLandingUrl;
    }

    public void setGoneLandingUrl(String goneLandingUrl) {
        this.goneLandingUrl = goneLandingUrl;
    }

    public String getQueryForwardMode() {
        return queryForwardMode;
    }

    public void setQueryForwardMode(String queryForwardMode) {
        this.queryForwardMode = queryForwardMode;
    }

    public List<String> getQueryForwardAllowlist() {
        return queryForwardAllowlist;
    }

    public void setQueryForwardAllowlist(List<String> queryForwardAllowlist) {
        this.queryForwardAllowlist = queryForwardAllowlist;
    }

    public List<String> getQueryForwardReservedParams() {
        return queryForwardReservedParams;
    }

    public void setQueryForwardReservedParams(List<String> queryForwardReservedParams) {
        this.queryForwardReservedParams = queryForwardReservedParams;
    }
}

