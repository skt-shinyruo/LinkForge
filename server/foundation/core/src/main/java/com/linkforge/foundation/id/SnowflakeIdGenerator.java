package com.linkforge.foundation.id;

/**
 * 生成进程内单调的 Snowflake 64-bit ID。
 *
 * <p>epoch 固定为 2024-01-01T00:00:00Z，节点号各占 5 bit，序列占 12 bit。Spring 运行时从
 * {@code app.id.worker-id/datacenter-id} 注入节点号；无参构造的 1/1 仅供直接构造兼容。</p>
 *
 * <p>同毫秒序列耗尽或时钟回拨时会同步等待，较大回拨因此可能阻塞调用线程。多实例节点组合重复会
 * 破坏唯一性，必须依赖启动门禁和部署配置避免。</p>
 */
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1704067200000L; // 2024-01-01T00:00:00Z

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private final long workerId;
    private final long datacenterId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * 使用兼容节点组合 1/1 构造生成器。
     *
     * <p>仅适用于直接构造或本地运行；生产 Spring 应用应使用 {@code IdProperties} 注入的显式节点号。</p>
     */
    public SnowflakeIdGenerator() {
        // 直接构造的兼容默认值；Spring 运行时使用配置化构造器。
        this(1L, 1L);
    }

    /**
     * 使用部署分配的 worker/datacenter 节点号构造生成器。
     *
     * @throws IllegalArgumentException 任一节点号不在 0..31 时抛出
     */
    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId out of range");
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId out of range");
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /**
     * 生成该进程内严格递增的 ID。
     *
     * <p>方法同步以保护毫秒序列；同毫秒的 4096 个序列耗尽或系统时间回拨时会自旋等待下一毫秒。因此调用
     * 可能阻塞，且不同节点之间不保证 ID 的全局时间顺序。跨进程唯一性仍依赖节点组合不重复。</p>
     */
    public synchronized long nextId() {
        long timestamp = currentTime();
        if (timestamp < lastTimestamp) {
            // 回拨时等待而不是生成倒退时间位；长时间等待说明宿主机时钟异常，应由运维处理。
            timestamp = waitNextMillis(lastTimestamp);
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long lastTs) {
        long ts = currentTime();
        while (ts <= lastTs) {
            ts = currentTime();
        }
        return ts;
    }

    private long currentTime() {
        return System.currentTimeMillis();
    }
}
