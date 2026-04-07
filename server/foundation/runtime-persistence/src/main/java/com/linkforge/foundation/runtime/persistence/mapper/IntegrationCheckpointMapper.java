package com.linkforge.foundation.runtime.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IntegrationCheckpointMapper {

    Long findLastSeq(@Param("consumer") String consumer);

    int insert(@Param("consumer") String consumer, @Param("lastSeq") long lastSeq);

    int update(@Param("consumer") String consumer, @Param("lastSeq") long lastSeq);
}

