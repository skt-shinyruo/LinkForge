package com.linkforge.foundation.eventing;

public interface IntegrationCheckpointRepository {
    long loadOrInit(String consumer);

    void update(String consumer, long lastSeq);
}

