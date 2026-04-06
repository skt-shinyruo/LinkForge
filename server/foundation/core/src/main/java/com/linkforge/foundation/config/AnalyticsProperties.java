package com.linkforge.foundation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.analytics")
public class AnalyticsProperties {

    private String salt;
    private long redisKeyTtlDays;

    /**
     * flush 回补窗口（天数，包含今天）。
     *
     * <p>用于定时作业停摆/部署中断后的追赶补齐：会尝试 flush 最近 N 天的 active-set。</p>
     */
    private int flushBackfillDays = 7;

    /**
     * 允许采集的“追踪类”Query 参数白名单（可选，支持前缀通配：utm_*）。
     *
     * <p>说明：该白名单仅用于统计维度/明细采集，不影响 Redirect 的 query 透传策略。</p>
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
         */
        private List<String> types;

        /**
         * 每次 flush（按天）最多处理的活跃短链数上限。
         */
        private int maxLinksPerDay = 5000;

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

        public int getMaxLinksPerDay() {
            return maxLinksPerDay;
        }

        public void setMaxLinksPerDay(int maxLinksPerDay) {
            this.maxLinksPerDay = maxLinksPerDay;
        }
    }

    public static class Events {
        /**
         * 是否启用访问明细事件（Edge → Redis Stream → API 异步落库）。
         */
        private boolean enabled;

        /**
         * 采样率（0~1）。
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
         */
        private String consumerName;

        /**
         * 是否启用 pending reclaim。
         */
        private boolean pendingReclaimEnabled = true;

        private long pendingReclaimMinIdleMs = 60_000;
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
         * Redis/DB 异常时是否放行（fail-open）。默认 true。
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

