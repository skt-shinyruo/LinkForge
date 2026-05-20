package com.linkforge.shortlink.infrastructure.persistence.mapper;

import com.linkforge.shortlink.infrastructure.redirect.RedirectCacheInvalidationOutboxRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RedirectCacheInvalidationOutboxMapper {

    int enqueue(
            @Param("tenantId") long tenantId,
            @Param("domainId") Long domainId,
            @Param("domainScope") long domainScope,
            @Param("code") String code,
            @Param("nextAttemptAtUtc") LocalDateTime nextAttemptAtUtc
    );

    List<RedirectCacheInvalidationOutboxRow> listDue(
            @Param("nowUtc") LocalDateTime nowUtc,
            @Param("limit") int limit
    );

    int markProcessed(
            @Param("id") long id,
            @Param("processedAtUtc") LocalDateTime processedAtUtc
    );

    int markFailed(
            @Param("id") long id,
            @Param("attempts") int attempts,
            @Param("lastError") String lastError,
            @Param("nextAttemptAtUtc") LocalDateTime nextAttemptAtUtc
    );
}
