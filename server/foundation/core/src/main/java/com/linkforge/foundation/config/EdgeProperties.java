package com.linkforge.foundation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Redirect Edge 的可信代理与风险控制配置。
 *
 * <p>代理信任是安全边界：只有 TCP 对端 {@code remoteAddr} 命中 {@code trustedProxies} 时，客户端 IP
 * 解析器才读取 {@code X-Real-IP}/{@code X-Forwarded-For}。当前实现不解析标准 {@code Forwarded} 头；
 * 未配置受信代理时一律使用 {@code remoteAddr}，不能由客户端自行伪造。</p>
 */
@ConfigurationProperties(prefix = "app.edge")
public class EdgeProperties {

    /**
     * 受信代理 CIDR 列表，仅当 {@code remoteAddr} 命中时才采信 {@code X-Real-IP}/{@code X-Forwarded-For}。
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

    /** IP 白/黑名单、速率限制与 Bot 策略的组合配置。 */
    public static class RiskControl {

        /** 是否启用 Redirect 风险控制；关闭时其余子项不参与判定。 */
        private boolean enabled;

        /** 允许访问的 IP/CIDR 列表；非空时未命中地址会被拒绝，denylist 仍优先。 */
        private List<String> ipAllowlist;

        /** 被拒绝的 IP/CIDR 列表。 */
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

        /** Redis 支持的按 IP 或 IP+短码速率限制配置。 */
        public static class RateLimit {

            /** 是否启用速率限制。 */
            private boolean enabled;

            /** 固定窗口长度，单位为秒，默认 60。 */
            private int windowSeconds = 60;

            /** 单 IP 在一个窗口内允许的最大请求数，默认 120。 */
            private long ipMaxRequests = 120;

            /** 是否同时限制单个 IP 对同一短码的访问。 */
            private boolean ipCodeEnabled;

            /** IP+短码窗口最大请求数，默认 60。 */
            private long ipCodeMaxRequests = 60;

            /**
             * Redis 限流基础设施异常时是否放行，默认 {@code true}。
             *
             * <p>设为 {@code false} 会将限流依赖故障转为拒绝访问；它只影响基础设施异常，不覆盖正常的配额
             * 超限判定。</p>
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

        /** 基于 User-Agent 关键字与单 IP 频率的 Bot 策略。 */
        public static class BotPolicy {

            /** 是否启用 Bot 策略。 */
            private boolean enabled;

            /** 用于大小写不敏感匹配 User-Agent 的关键字列表。 */
            private List<String> userAgentKeywords;

            /** Bot 命中且基础速率限制已开启时的单 IP 最大请求数，默认 30。 */
            private long ipMaxRequests = 30;

            /**
             * 是否将命中策略的请求直接以 403 阻断。
             *
             * <p>为 {@code false} 时，若基础速率限制开启则改用本策略的 {@code ipMaxRequests}；基础限流也
             * 关闭时，Bot 检测本身不产生拒绝动作。</p>
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
