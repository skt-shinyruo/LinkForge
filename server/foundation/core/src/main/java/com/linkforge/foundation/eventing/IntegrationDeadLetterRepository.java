package com.linkforge.foundation.eventing;

public interface IntegrationDeadLetterRepository {
    void upsertFailure(String consumer, IntegrationEventRow event, int attempts, String lastError);
}

