package com.linkforge.foundation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.edge")
public class EdgeProperties {

    /**
     * 受信代理列表（CIDR），仅当 remoteAddr 命中该列表时才采信 Forwarded / X-Forwarded-For / X-Real-IP。
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
        private boolean enabled;
        private List<String> ipAllowlist;
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
            private List<String> userAgentKeywords;
            private long ipMaxRequests = 30;
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

