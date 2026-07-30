package com.linkforge.foundation.eventing;

/**
 * 集成事件消费失败的诊断记录端口。
 *
 * <p>同一 {@code (consumer, seq)} 的重复失败应覆盖尝试次数和最近错误，便于排障。DLQ 是 best-effort 诊断
 * 记录，不是可靠投递队列，不会自动重放、确认或替代原消费者的重试策略。</p>
 */
public interface IntegrationDeadLetterRepository {

    /** 记录或更新一次消费失败；调用方决定 DLQ 写入失败是否中断本轮消费。 */
    void upsertFailure(String consumer, IntegrationEventRow event, int attempts, String lastError);
}
