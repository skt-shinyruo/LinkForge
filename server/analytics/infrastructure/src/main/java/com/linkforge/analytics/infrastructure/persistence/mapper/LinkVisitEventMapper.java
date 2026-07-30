package com.linkforge.analytics.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 访问明细的写入与留存 SQL mapper。
 *
 * <p>批量写入以 requestId 唯一键冲突时的 no-op 实现重放幂等；清理 SQL 每次限制 5,000 行，由调度任务
 * 控制批次数，避免单条大删除长期持锁。</p>
 */
@Mapper
public interface LinkVisitEventMapper {

    /** 插入明细；重复 requestId 不改变既有记录。 */
    int batchInsertIgnore(List<LinkVisitEventInsertRow> rows);

    /** 删除创建时间早于给定留存天数的至多一批记录。 */
    int deleteOld(int retentionDays);
}
