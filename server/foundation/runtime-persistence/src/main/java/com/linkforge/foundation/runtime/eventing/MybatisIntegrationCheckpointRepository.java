package com.linkforge.foundation.runtime.eventing;

import com.linkforge.foundation.eventing.IntegrationCheckpointRepository;
import com.linkforge.foundation.runtime.persistence.mapper.IntegrationCheckpointMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisIntegrationCheckpointRepository implements IntegrationCheckpointRepository {

    private final IntegrationCheckpointMapper mapper;

    public MybatisIntegrationCheckpointRepository(IntegrationCheckpointMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long loadOrInit(String consumer) {
        Long existing = mapper.findLastSeq(consumer);
        if (existing != null) {
            return existing;
        }
        mapper.insert(consumer, 0L);
        return 0L;
    }

    @Override
    public void update(String consumer, long lastSeq) {
        mapper.update(consumer, lastSeq);
    }
}
