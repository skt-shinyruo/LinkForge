package com.linkforge.shortlink.infrastructure.persistence.mapper;

import com.linkforge.shortlink.infrastructure.redirect.RedirectCacheInvalidationOutboxRow;
import com.linkforge.shortlink.infrastructure.redirect.RedirectCacheInvalidationOutboxStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 缓存失效 outbox 的 SQL 映射。
 *
 * <p>入队 SQL 通过唯一键实现合并：相同短链再次变化时会递增 generation 并重新打开既有任务。状态更新
 * 同时校验 {@code status = PENDING} 与 worker 读取到的 generation，避免晚到结果覆盖更新一代的意图；
 * 到期查询不加行锁，正常生产调度仍由上层 ShedLock 串行。</p>
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

    RedirectCacheInvalidationOutboxStats pendingStats();

    int markProcessed(
            @Param("id") long id,
            @Param("generation") long generation,
            @Param("processedAtUtc") LocalDateTime processedAtUtc
    );

    int markFailed(
            @Param("id") long id,
            @Param("generation") long generation,
            @Param("attempts") int attempts,
            @Param("lastError") String lastError,
            @Param("nextAttemptAtUtc") LocalDateTime nextAttemptAtUtc
    );
}
