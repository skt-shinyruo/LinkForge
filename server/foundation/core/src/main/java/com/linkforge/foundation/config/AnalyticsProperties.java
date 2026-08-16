package com.linkforge.foundation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Instant;
import java.util.List;

/**
 * Analytics 聚合、Redis 流和访问明细的运行时配置。
 *
 * <p>所有按日聚合和访客指纹口径使用 UTC。标准访问事件以 requestId 做幂等聚合投影，Redis Stream
 * 重放不会重复增加 PV；历史上缺少 requestId 的兼容消息仍是至少一次语义，UV 由近似去重结构计算。</p>
 */
@ConfigurationProperties(prefix = "app.analytics")
public class AnalyticsProperties {

    /** 用于访客/IP 指纹哈希的私有盐；启动检查要求非空且严格模式拒绝明显开发默认值。 */
    private String salt;

    /** Analytics Redis key 的保留时间，单位为天；启动检查要求大于 0。 */
    private long redisKeyTtlDays;

    /**
     * flush 回补窗口（天数，包含今天）。
     *
     * <p>用于定时作业停摆/部署中断后的追赶补齐：会尝试刷新最近 N 天的 V2 marker，并在兼容期开启时
     * 继续消费 legacy dirty Stream。
     * 窗口会被 Redis key TTL 截断。</p>
     */
    private int flushBackfillDays = 7;

    /**
     * 允许采集的“追踪类”Query 参数白名单（可选，支持前缀通配：utm_*）。
     *
     * <p>说明：该白名单仅用于统计维度/明细采集，不影响 Redirect 的 query 透传策略。</p>
     */
    private List<String> trackingParamAllowlist;

    private Dimensions dimensions = new Dimensions();
    private VisitStream visitStream = new VisitStream();
    private DirtyMarker dirtyMarker = new DirtyMarker();
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

    public VisitStream getVisitStream() {
        return visitStream;
    }

    public void setVisitStream(VisitStream visitStream) {
        this.visitStream = visitStream;
    }

    public DirtyMarker getDirtyMarker() {
        return dirtyMarker;
    }

    public void setDirtyMarker(DirtyMarker dirtyMarker) {
        this.dirtyMarker = dirtyMarker;
    }

    /**
     * 解析访问流近似长度上限。
     *
     * <p>独立的 {@code visit-stream.max-len} 非 {@code null} 时优先使用；否则兼容回退到
     * {@code events.stream-max-len}。最终值小于等于 0 表示 Redis 写入后不主动 trim，可能导致内存持续增长。</p>
     */
    public long resolveVisitStreamMaxLen() {
        Long dedicated = visitStream == null ? null : visitStream.getMaxLen();
        if (dedicated != null) {
            return dedicated;
        }
        return events == null ? 200_000L : events.getStreamMaxLen();
    }

    /** 返回峰值写入在恢复窗口内、计入安全余量后的最小 Stream 容量。 */
    public long resolveVisitStreamRequiredCapacity() {
        VisitStream cfg = visitStream == null ? new VisitStream() : visitStream;
        double required = (double) cfg.getPeakEventsPerSecond()
                * cfg.getRecoveryWindowSeconds()
                * (100L + cfg.getSafetyMarginPercent()) / 100.0d;
        if (required >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max((long) Math.ceil(required), 0L);
    }

    /** 聚合维度写入与落库的开关及允许类型。 */
    public static class Dimensions {
        /**
         * 是否启用维度统计写入（Edge 写 Redis；API flush 落库）。
         */
        private boolean enabled;

        /**
         * 启用的维度类型列表（可选，为空时使用默认推荐集合）。
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

    /**
     * 完整访问事件 Redis Stream 的容量控制。
     *
     * <p>{@code null} 保持兼容并回退到 {@code events.stream-max-len}；正值使用 Redis approximate trim，
     * 小于等于 0 关闭 trim。</p>
     */
    public static class VisitStream {
        /**
         * 完整重定向访问流的 Redis Stream 近似最大长度。
         *
         * <p>空值回退到 {@code events.stream-max-len}；小于等于 0 表示关闭 trim。</p>
         */
        private Long maxLen;

        /** 已记录容量基线的峰值访问事件速率。 */
        private long peakEventsPerSecond = 1_000L;

        /** 消费者中断后必须可恢复的时间窗口。 */
        private long recoveryWindowSeconds = 180L;

        /** 在峰值乘恢复窗口之上的额外容量百分比。 */
        private int safetyMarginPercent = 10;

        public Long getMaxLen() {
            return maxLen;
        }

        public void setMaxLen(Long maxLen) {
            this.maxLen = maxLen;
        }

        public long getPeakEventsPerSecond() {
            return peakEventsPerSecond;
        }

        public void setPeakEventsPerSecond(long peakEventsPerSecond) {
            this.peakEventsPerSecond = peakEventsPerSecond;
        }

        public long getRecoveryWindowSeconds() {
            return recoveryWindowSeconds;
        }

        public void setRecoveryWindowSeconds(long recoveryWindowSeconds) {
            this.recoveryWindowSeconds = recoveryWindowSeconds;
        }

        public int getSafetyMarginPercent() {
            return safetyMarginPercent;
        }

        public void setSafetyMarginPercent(int safetyMarginPercent) {
            this.safetyMarginPercent = safetyMarginPercent;
        }
    }

    /** V2 generation marker 与 legacy dirty Stream 的滚动升级开关。 */
    public static class DirtyMarker {
        /** 回滚窗口内可显式恢复 additive legacy 写入；迁移后的默认生产者只写 V2。 */
        private boolean legacyWriteEnabled;
        /** 兼容期继续排空旧实例和历史数据，默认开启。 */
        private boolean legacyReadEnabled = true;
        /** 运维侧确认 legacy 已排空且持续无新写入；单独设置不允许退役。 */
        private boolean legacyRetirementConfirmed;
        /** 停止所有 legacy producer 后至少等待的兼容 TTL。 */
        private int compatibilityTtlDays = 45;
        /** 所有 legacy producer 停止写入的可审计时间。 */
        private Instant legacyWriteStoppedAt;
        /** legacy streams 首次确认全部排空的可审计时间。 */
        private Instant legacyDrainedAt;

        public boolean isLegacyWriteEnabled() {
            return legacyWriteEnabled;
        }

        public void setLegacyWriteEnabled(boolean legacyWriteEnabled) {
            this.legacyWriteEnabled = legacyWriteEnabled;
        }

        public boolean isLegacyReadEnabled() {
            return legacyReadEnabled;
        }

        public void setLegacyReadEnabled(boolean legacyReadEnabled) {
            this.legacyReadEnabled = legacyReadEnabled;
        }

        public boolean isLegacyRetirementConfirmed() {
            return legacyRetirementConfirmed;
        }

        public void setLegacyRetirementConfirmed(boolean legacyRetirementConfirmed) {
            this.legacyRetirementConfirmed = legacyRetirementConfirmed;
        }

        public int getCompatibilityTtlDays() {
            return compatibilityTtlDays;
        }

        public void setCompatibilityTtlDays(int compatibilityTtlDays) {
            this.compatibilityTtlDays = compatibilityTtlDays;
        }

        public Instant getLegacyWriteStoppedAt() {
            return legacyWriteStoppedAt;
        }

        public void setLegacyWriteStoppedAt(Instant legacyWriteStoppedAt) {
            this.legacyWriteStoppedAt = legacyWriteStoppedAt;
        }

        public Instant getLegacyDrainedAt() {
            return legacyDrainedAt;
        }

        public void setLegacyDrainedAt(Instant legacyDrainedAt) {
            this.legacyDrainedAt = legacyDrainedAt;
        }
    }

    /**
     * 访问明细流的异步消费与保留配置。
     *
     * <p>该开关不关闭 Redirect 主链路的访问记录调用，也不控制聚合 dirty marker；{@code enabled=false}
     * 只使明细 Stream 的消费/落库和保留作业跳过。写入端的异常是否影响重定向由 {@code failOpen} 决定。</p>
     */
    public static class Events {
        /**
         * 是否启用访问明细 Stream 的异步消费和落库。
         *
         * <p>关闭时已有 Stream 数据不会自动删除，且不改变主访问量聚合；重新开启后消费者可继续读取保留消息。</p>
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

        /** 单次 Redis Stream 读取的最大记录数。 */
        private int ingestBatchSize = 200;

        /** 单次调度最多处理的批次数，防止持续 backlog 独占调度线程。 */
        private int ingestMaxBatches = 10;

        /** 单次调度允许用于恢复 backlog 的最长时间。 */
        private long ingestTimeBudgetMs = 1_000L;

        /**
         * UA 原始字符串最大长度（落库与 stream 写入均会截断）。
         */
        private int maxUserAgentLength = 512;

        /**
         * 追踪参数（utm_* 等）的 value 最大长度（落库与 stream 写入均会截断）。
         */
        private int maxTrackingValueLength = 128;

        /**
         * 访问事件写入器发生运行时异常时是否放行重定向主链路，默认 {@code true}。
         *
         * <p>设为 {@code false} 时异常会向 Redirect 主链路传播；它不改变异步消费作业自身的 best-effort
         * 重试策略。</p>
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

        public int getIngestBatchSize() {
            return ingestBatchSize;
        }

        public void setIngestBatchSize(int ingestBatchSize) {
            this.ingestBatchSize = ingestBatchSize;
        }

        public int getIngestMaxBatches() {
            return ingestMaxBatches;
        }

        public void setIngestMaxBatches(int ingestMaxBatches) {
            this.ingestMaxBatches = ingestMaxBatches;
        }

        public long getIngestTimeBudgetMs() {
            return ingestTimeBudgetMs;
        }

        public void setIngestTimeBudgetMs(long ingestTimeBudgetMs) {
            this.ingestTimeBudgetMs = ingestTimeBudgetMs;
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
