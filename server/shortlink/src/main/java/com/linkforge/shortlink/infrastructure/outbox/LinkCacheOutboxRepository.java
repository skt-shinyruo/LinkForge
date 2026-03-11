package com.linkforge.shortlink.infrastructure.outbox;

import com.linkforge.shortlink.infrastructure.persistence.mapper.LinkCacheOutboxMapper;
import com.linkforge.shortlink.infrastructure.persistence.mapper.LinkCacheOutboxPendingRow;
import com.linkforge.shortlink.infrastructure.persistence.mapper.LinkCacheOutboxStatsRow;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 短链缓存刷新 Outbox（持久化）。
 *
 * <p>语义：对某个 code 触发一次“按 DB 当前状态刷新缓存”的动作。</p>
 * <ul>
 *   <li>存在且未归档：写入 LinkMeta 到 Redis</li>
 *   <li>不存在或已归档：驱逐 Redis key</li>
 * </ul>
 *
 * <p>说明：以 code 为主键，天然合并/去重，避免高频更新产生大量 outbox 行。</p>
 */
@Repository
public class LinkCacheOutboxRepository {

    private final LinkCacheOutboxMapper outboxMapper;

    public LinkCacheOutboxRepository(LinkCacheOutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    public record PendingItem(String code, int attempts) {
    }

    public record OutboxStats(long pendingTotal, long pendingReady, long pendingLagSeconds) {
    }

    public void enqueueRefresh(String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        String c = code.trim();
        outboxMapper.enqueueRefresh(c);
    }

    public List<PendingItem> listPending(int limit) {
        int n = Math.max(1, Math.min(limit, 1000));
        List<LinkCacheOutboxPendingRow> rows = outboxMapper.listPending(n);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<PendingItem> out = new ArrayList<>(rows.size());
        for (LinkCacheOutboxPendingRow r : rows) {
            if (r == null || r.getCode() == null) {
                continue;
            }
            int attempts = r.getAttempts() == null ? 0 : r.getAttempts();
            out.add(new PendingItem(r.getCode(), attempts));
        }
        return out;
    }

    public void markDone(String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        outboxMapper.markDone(code.trim());
    }

    public void markRetry(String code, int attempts, String lastError, long delaySeconds) {
        if (code == null || code.isBlank()) {
            return;
        }
        int nextAttempts = Math.max(attempts, 1);
        long delay = Math.max(1, Math.min(delaySeconds, 3600));
        String err = lastError == null ? null : truncate(lastError, 512);
        outboxMapper.markRetry(code.trim(), nextAttempts, err, delay);
    }

    public int deleteDoneOlderThanDays(int retentionDays, int limit) {
        int days = Math.max(retentionDays, 0);
        if (days <= 0) {
            return 0;
        }
        int n = Math.max(1, Math.min(limit, 50_000));
        return outboxMapper.deleteDoneOlderThanDays(days, n);
    }

    public OutboxStats loadStats() {
        LinkCacheOutboxStatsRow row = outboxMapper.loadStats();
        if (row == null) {
            return new OutboxStats(0, 0, 0);
        }
        return new OutboxStats(
                safeLong(row.getPendingTotal()),
                safeLong(row.getPendingReady()),
                safeLong(row.getPendingLagSeconds())
        );
    }

    public String findStatusByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return outboxMapper.findStatusByCode(code.trim());
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen);
    }

    private static long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
