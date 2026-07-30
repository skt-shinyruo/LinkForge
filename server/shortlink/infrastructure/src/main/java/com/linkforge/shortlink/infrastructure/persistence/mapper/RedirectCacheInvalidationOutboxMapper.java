package com.linkforge.shortlink.infrastructure.persistence.mapper;

import com.linkforge.shortlink.infrastructure.redirect.RedirectCacheInvalidationOutboxRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 缓存失效 outbox 的 SQL 映射。
 *
 * <p>入队 SQL 通过唯一键实现合并：相同短链再次变化时会重新打开既有任务。状态更新都带
 * {@code status = PENDING} 条件，避免晚到的处理结果覆盖已完成状态；到期查询不加行锁，必须由上层调度锁
 * 保证单消费者运行。</p>
 */
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
