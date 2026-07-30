package com.linkforge.foundation.runtime.eventing;

import com.linkforge.foundation.eventing.IntegrationCheckpointRepository;
import com.linkforge.foundation.runtime.persistence.mapper.IntegrationCheckpointMapper;
import org.springframework.stereotype.Repository;

/**
 * 集成事件消费者游标的 MyBatis 适配器。
 *
 * <p>首次读取按“查找后插入”执行，因此同一 consumer 被多个实例同时初始化时可能触发数据库唯一键冲突；
 * 上层应避免并发启动同一消费者或将该冲突作为可重试条件。更新语句不含 {@code last_seq < newSeq} 条件，
 * 单调推进责任在调用方。</p>
 */
@Repository
public class MybatisIntegrationCheckpointRepository implements IntegrationCheckpointRepository {

    private final IntegrationCheckpointMapper mapper;

    public MybatisIntegrationCheckpointRepository(IntegrationCheckpointMapper mapper) {
        this.mapper = mapper;
    }

    /** 读取已有游标，不存在时插入初始值 0。 */
    @Override
    public long loadOrInit(String consumer) {
        Long existing = mapper.findLastSeq(consumer);
        if (existing != null) {
            return existing;
        }
        mapper.insert(consumer, 0L);
        return 0L;
    }

    /** 直接覆盖游标；调用方必须保证 consumer 串行且 lastSeq 不倒退。 */
    @Override
    public void update(String consumer, long lastSeq) {
        mapper.update(consumer, lastSeq);
    }
}
