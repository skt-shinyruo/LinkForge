package com.linkforge.foundation.eventing;

import com.linkforge.foundation.eventing.mapper.IntegrationDeadLetterMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisIntegrationDeadLetterRepository implements IntegrationDeadLetterRepository {

    private final IntegrationDeadLetterMapper mapper;

    public MybatisIntegrationDeadLetterRepository(IntegrationDeadLetterMapper mapper) {
        this.mapper = mapper;
    }

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

