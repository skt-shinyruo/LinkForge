package com.linkforge.foundation.runtime.eventing;

import com.linkforge.foundation.eventing.IntegrationDeadLetterRepository;
import com.linkforge.foundation.eventing.IntegrationEventRow;
import com.linkforge.foundation.runtime.persistence.mapper.IntegrationDeadLetterMapper;
import org.springframework.stereotype.Repository;

/**
 * 集成事件消费失败诊断的 MyBatis 适配器。
 *
 * <p>数据库以 {@code (consumer, seq)} 做 upsert：首次失败保存事件快照，后续失败只覆盖尝试次数和最近错误。
 * 本适配器不截断错误、不吞掉数据库异常，也不调度重放；调用方需按自身 best-effort 策略处理失败。</p>
 */
@Repository
public class MybatisIntegrationDeadLetterRepository implements IntegrationDeadLetterRepository {

    private final IntegrationDeadLetterMapper mapper;

    public MybatisIntegrationDeadLetterRepository(IntegrationDeadLetterMapper mapper) {
        this.mapper = mapper;
    }

    /** 将失败事件映射为稳定的 DLQ 行；event 为空会按调用约定触发空指针，避免悄然丢失诊断。 */
    @Override
    public void upsertFailure(String consumer, IntegrationEventRow event, int attempts, String lastError) {
        mapper.upsertFailure(
                consumer,
                event.seq(),
                event.eventId(),
                event.producer(),
                event.eventType(),
                event.occurredAtUtc(),
                event.payloadJson(),
                attempts,
                lastError
        );
    }
}
