package com.linkforge.foundation.runtime.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * integration_consumer_checkpoint 表的机械映射。
 *
 * <p>更新是无条件覆盖，不包含 compare-and-set；调用方负责同一 consumer 的串行和单调推进。</p>
 */
@Mapper
public interface IntegrationCheckpointMapper {

    /** 返回 consumer 当前游标；记录不存在时返回 {@code null}。 */
    Long findLastSeq(@Param("consumer") String consumer);

    /** 插入初始游标；并发重复初始化由数据库唯一约束处理。 */
    int insert(@Param("consumer") String consumer, @Param("lastSeq") long lastSeq);

    /** 无条件覆盖当前游标，受调用线程事务控制。 */
    int update(@Param("consumer") String consumer, @Param("lastSeq") long lastSeq);
}
