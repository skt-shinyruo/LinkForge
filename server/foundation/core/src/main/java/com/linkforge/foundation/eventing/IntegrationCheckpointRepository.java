package com.linkforge.foundation.eventing;

/**
 * 集成事件消费者的排他序号游标存储。
 *
 * <p>checkpoint 只能表示某消费者已成功处理到的 {@code seq}，不是消息确认或 exactly-once 证明。消费者应在
 * 自身可重试/幂等处理完成后再推进游标；当前端口不保证并发首次初始化的原子性，也不强制 update 单调递增。</p>
 */
public interface IntegrationCheckpointRepository {

    /** 读取消费者游标；不存在时初始化为 0 并返回 0。 */
    long loadOrInit(String consumer);

    /**
     * 写入已成功处理的最后序号。
     *
     * <p>调用方必须确保 {@code lastSeq} 不倒退，并串行化同一 consumer 的推进，避免重放范围扩大。</p>
     */
    void update(String consumer, long lastSeq);
}
