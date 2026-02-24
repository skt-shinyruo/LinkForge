package com.linkforge.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String baseUrl;
    private Id id = new Id();
    private Security security = new Security();
    private Cors cors = new Cors();
    private Redirect redirect = new Redirect();
    private Analytics analytics = new Analytics();
    private Edge edge = new Edge();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Id getId() {
        return id;
    }

    public void setId(Id id) {
        this.id = id;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public Redirect getRedirect() {
        return redirect;
    }

    public void setRedirect(Redirect redirect) {
        this.redirect = redirect;
    }

    public Analytics getAnalytics() {
        return analytics;
    }

    public void setAnalytics(Analytics analytics) {
        this.analytics = analytics;
    }

    public Edge getEdge() {
        return edge;
    }

    public void setEdge(Edge edge) {
        this.edge = edge;
    }

    public static class Security {
        private Jwt jwt = new Jwt();

        public Jwt getJwt() {
            return jwt;
        }

        public void setJwt(Jwt jwt) {
            this.jwt = jwt;
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
    }

    public static class Id {
        private long workerId = 1;
        private long datacenterId = 1;

        public long getWorkerId() {
            return workerId;
        }

        public void setWorkerId(long workerId) {
            this.workerId = workerId;
        }

        public long getDatacenterId() {
            return datacenterId;
        }

        public void setDatacenterId(long datacenterId) {
            this.datacenterId = datacenterId;
        }
    }

    public static class Cors {
        /**
         * 允许的来源白名单（支持逗号分隔环境变量绑定）。
         *
         * <p>未配置时默认使用 allowOriginPatterns="*" 且 allowCredentials=false（仅推荐用于本地开发）。</p>
         */
        private List<String> allowedOrigins;

        /**
         * 是否允许携带凭证（cookie 模式需要开启）。开启后必须使用明确白名单，禁止 "*"。
         */
        private boolean allowCredentials;

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }
    }

    public static class Redirect {
        private long cacheTtlSeconds;
        private int defaultStatusCode;
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

    public static class Analytics {
        private String salt;
        private long redisKeyTtlDays;
        /**
         * flush 回补窗口（天数，包含今天）。
         *
         * <p>用于定时作业停摆/部署中断后的追赶补齐：会尝试 flush 最近 N 天的 active-set。</p>
         * <p>注意：窗口越大，active-set 扫描与落库开销越高；建议结合数据规模调整。</p>
         */
        private int flushBackfillDays = 7;
        /**
         * 允许采集的“追踪类”Query 参数白名单（可选，支持前缀通配：utm_*）。
         *
         * <p>说明：该白名单仅用于统计维度/明细采集，不影响 Redirect 的 query 透传策略。</p>
         * <p>安全默认值在 Edge 侧做兜底：仅采集 utm_* / gclid / fbclid。</p>
         */
        private List<String> trackingParamAllowlist;

        private Dimensions dimensions = new Dimensions();
        private Events events = new Events();

        public String getSalt() {
            return salt;
        }

        public void setSalt(String salt) {
            this.salt = salt;
        }

        public long getRedisKeyTtlDays() {
            return redisKeyTtlDays;
        }

        public void setRedisKeyTtlDays(long redisKeyTtlDays) {
            this.redisKeyTtlDays = redisKeyTtlDays;
        }

        public int getFlushBackfillDays() {
            return flushBackfillDays;
        }

        public void setFlushBackfillDays(int flushBackfillDays) {
            this.flushBackfillDays = flushBackfillDays;
        }

        public List<String> getTrackingParamAllowlist() {
            return trackingParamAllowlist;
        }

        public void setTrackingParamAllowlist(List<String> trackingParamAllowlist) {
            this.trackingParamAllowlist = trackingParamAllowlist;
        }

        public Dimensions getDimensions() {
            return dimensions;
        }

        public void setDimensions(Dimensions dimensions) {
            this.dimensions = dimensions;
        }

        public Events getEvents() {
            return events;
        }

        public void setEvents(Events events) {
            this.events = events;
        }

        public static class Dimensions {
            /**
             * 是否启用维度统计写入（Edge 写 Redis；API flush 落库）。
             */
            private boolean enabled;

            /**
             * 启用的维度类型列表（可选，为空时使用默认推荐集合）。
             *
             * <p>示例：referer_domain, language, ua_family, os_family, device_type, utm_source</p>
             */
            private List<String> types;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public List<String> getTypes() {
                return types;
            }

            public void setTypes(List<String> types) {
                this.types = types;
            }
        }

        public static class Events {
            /**
             * 是否启用访问明细事件（Edge → Redis Stream → API 异步落库）。
             */
            private boolean enabled;

            /**
             * 采样率（0~1）。默认建议小流量启用可设为 1；公网高 QPS 场景建议 0.01~0.2。
             */
            private double sampleRate = 0.1;

            /**
             * Redis Stream 近似最大长度（用于控制内存），<=0 表示不 trim（不推荐）。
             */
            private long streamMaxLen = 200_000;

            /**
             * 访问明细保留天数（用于定期清理），<=0 表示不清理（不推荐）。
             */
            private int retentionDays = 14;

            /**
             * Redis Stream consumer 名称（可选）。
             *
             * <p>建议：多实例部署时每个实例使用不同 consumerName（支持并行消费）；同时保持稳定以便重启后继续处理 pending。</p>
             * <p>未配置时，API 会基于 HOSTNAME + workerId/datacenterId 派生一个相对稳定的 consumer。</p>
             */
            private String consumerName;

            /**
             * 是否启用 pending reclaim（通过 XPENDING + XCLAIM 接管已下线 consumer 的 pending）。
             *
             * <p>用于处理 consumer 漂移/重启导致的 pending 积压，避免明细长期不落库。</p>
             */
            private boolean pendingReclaimEnabled = true;

            /**
             * pending reclaim 的最小 idle 时间（毫秒）。仅接管“已闲置超过该阈值”的 pending，避免抢占正在处理的 consumer。
             */
            private long pendingReclaimMinIdleMs = 60_000;

            /**
             * 每轮 pending reclaim 最多尝试接管的消息数。
             */
            private int pendingReclaimCount = 200;

            /**
             * UA 原始字符串最大长度（落库与 stream 写入均会截断）。
             */
            private int maxUserAgentLength = 512;

            /**
             * 追踪参数（utm_* 等）的 value 最大长度（落库与 stream 写入均会截断）。
             */
            private int maxTrackingValueLength = 128;

            /**
             * Redis/DB 异常时是否放行（fail-open）。默认 true，确保跳转主链路不被统计拖垮。
             */
            private boolean failOpen = true;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public double getSampleRate() {
                return sampleRate;
            }

            public void setSampleRate(double sampleRate) {
                this.sampleRate = sampleRate;
            }

            public long getStreamMaxLen() {
                return streamMaxLen;
            }

            public void setStreamMaxLen(long streamMaxLen) {
                this.streamMaxLen = streamMaxLen;
            }

            public int getRetentionDays() {
                return retentionDays;
            }

            public void setRetentionDays(int retentionDays) {
                this.retentionDays = retentionDays;
            }

            public String getConsumerName() {
                return consumerName;
            }

            public void setConsumerName(String consumerName) {
                this.consumerName = consumerName;
            }

            public boolean isPendingReclaimEnabled() {
                return pendingReclaimEnabled;
            }

            public void setPendingReclaimEnabled(boolean pendingReclaimEnabled) {
                this.pendingReclaimEnabled = pendingReclaimEnabled;
            }

            public long getPendingReclaimMinIdleMs() {
                return pendingReclaimMinIdleMs;
            }

            public void setPendingReclaimMinIdleMs(long pendingReclaimMinIdleMs) {
                this.pendingReclaimMinIdleMs = pendingReclaimMinIdleMs;
            }

            public int getPendingReclaimCount() {
                return pendingReclaimCount;
            }

            public void setPendingReclaimCount(int pendingReclaimCount) {
                this.pendingReclaimCount = pendingReclaimCount;
            }

            public int getMaxUserAgentLength() {
                return maxUserAgentLength;
            }

            public void setMaxUserAgentLength(int maxUserAgentLength) {
                this.maxUserAgentLength = maxUserAgentLength;
            }

            public int getMaxTrackingValueLength() {
                return maxTrackingValueLength;
            }

            public void setMaxTrackingValueLength(int maxTrackingValueLength) {
                this.maxTrackingValueLength = maxTrackingValueLength;
            }

            public boolean isFailOpen() {
                return failOpen;
            }

            public void setFailOpen(boolean failOpen) {
                this.failOpen = failOpen;
            }
        }
    }

    /**
     * Redirect Edge 的生产化加固配置（可信代理链、防滥用/风控）。
     *
     * <p>说明：配置缺省时采用“安全默认值”（不信任 forwarded headers；风控默认关闭或宽松）。</p>
     */
    public static class Edge {

        /**
         * 受信代理列表（CIDR），仅当 remoteAddr 命中该列表时才采信 Forwarded / X-Forwarded-For / X-Real-IP。
         *
         * <p>示例：10.0.0.0/8,192.168.0.0/16 或某个固定网关 IP。</p>
         */
        private List<String> trustedProxies;

        private RiskControl riskControl = new RiskControl();

        public List<String> getTrustedProxies() {
            return trustedProxies;
        }

        public void setTrustedProxies(List<String> trustedProxies) {
            this.trustedProxies = trustedProxies;
        }

        public RiskControl getRiskControl() {
            return riskControl;
        }

        public void setRiskControl(RiskControl riskControl) {
            this.riskControl = riskControl;
        }

        public static class RiskControl {
            /**
             * 是否启用 /r/** 风控（仅影响 Edge 服务）。
             *
             * <p>注意：若未正确配置 trusted-proxies 且流量经网关转发，按 IP 限流可能会聚合到网关 IP。</p>
             */
            private boolean enabled;

            /**
             * 允许访问的 IP 白名单（CIDR）。为空表示不启用白名单。
             */
            private List<String> ipAllowlist;

            /**
             * 禁止访问的 IP 黑名单（CIDR）。
             */
            private List<String> ipDenylist;

            private RateLimit rateLimit = new RateLimit();
            private BotPolicy bot = new BotPolicy();

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public List<String> getIpAllowlist() {
                return ipAllowlist;
            }

            public void setIpAllowlist(List<String> ipAllowlist) {
                this.ipAllowlist = ipAllowlist;
            }

            public List<String> getIpDenylist() {
                return ipDenylist;
            }

            public void setIpDenylist(List<String> ipDenylist) {
                this.ipDenylist = ipDenylist;
            }

            public RateLimit getRateLimit() {
                return rateLimit;
            }

            public void setRateLimit(RateLimit rateLimit) {
                this.rateLimit = rateLimit;
            }

            public BotPolicy getBot() {
                return bot;
            }

            public void setBot(BotPolicy bot) {
                this.bot = bot;
            }

            public static class RateLimit {
                private boolean enabled;
                private int windowSeconds = 60;
                private long ipMaxRequests = 120;
                private boolean ipCodeEnabled;
                private long ipCodeMaxRequests = 60;

                /**
                 * Redis 异常时是否放行（fail-open）。
                 */
                private boolean failOpen = true;

                public boolean isEnabled() {
                    return enabled;
                }

                public void setEnabled(boolean enabled) {
                    this.enabled = enabled;
                }

                public int getWindowSeconds() {
                    return windowSeconds;
                }

                public void setWindowSeconds(int windowSeconds) {
                    this.windowSeconds = windowSeconds;
                }

                public long getIpMaxRequests() {
                    return ipMaxRequests;
                }

                public void setIpMaxRequests(long ipMaxRequests) {
                    this.ipMaxRequests = ipMaxRequests;
                }

                public boolean isIpCodeEnabled() {
                    return ipCodeEnabled;
                }

                public void setIpCodeEnabled(boolean ipCodeEnabled) {
                    this.ipCodeEnabled = ipCodeEnabled;
                }

                public long getIpCodeMaxRequests() {
                    return ipCodeMaxRequests;
                }

                public void setIpCodeMaxRequests(long ipCodeMaxRequests) {
                    this.ipCodeMaxRequests = ipCodeMaxRequests;
                }

                public boolean isFailOpen() {
                    return failOpen;
                }

                public void setFailOpen(boolean failOpen) {
                    this.failOpen = failOpen;
                }
            }

            public static class BotPolicy {
                private boolean enabled;

                /**
                 * 识别 bot 的 UA 关键字（包含匹配）。为空表示不启用 bot 识别。
                 */
                private List<String> userAgentKeywords;

                /**
                 * bot 流量更严格的单 IP 阈值（与 rateLimit.windowSeconds 同窗口）。
                 */
                private long ipMaxRequests = 30;

                /**
                 * 是否对 bot 直接拒绝（403）。默认建议 false（降频优先，降低误伤）。
                 */
                private boolean block;

                public boolean isEnabled() {
                    return enabled;
                }

                public void setEnabled(boolean enabled) {
                    this.enabled = enabled;
                }

                public List<String> getUserAgentKeywords() {
                    return userAgentKeywords;
                }

                public void setUserAgentKeywords(List<String> userAgentKeywords) {
                    this.userAgentKeywords = userAgentKeywords;
                }

                public long getIpMaxRequests() {
                    return ipMaxRequests;
                }

                public void setIpMaxRequests(long ipMaxRequests) {
                    this.ipMaxRequests = ipMaxRequests;
                }

                public boolean isBlock() {
                    return block;
                }

                public void setBlock(boolean block) {
                    this.block = block;
                }
            }
        }
    }
}
