package com.linkforge.api.shortlink.cache;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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

    private final JdbcTemplate jdbcTemplate;

    public LinkCacheOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
        jdbcTemplate.update(
                """
                        INSERT INTO link_cache_outbox (code, status, available_at, attempts, last_error, processed_at)
                        VALUES (?, 'PENDING', NOW(), 0, NULL, NULL)
                        ON DUPLICATE KEY UPDATE
                          status = 'PENDING',
                          available_at = NOW(),
                          attempts = 0,
                          last_error = NULL,
                          processed_at = NULL
                        """,
                c
        );
    }

    public List<PendingItem> listPending(int limit) {
        int n = Math.max(1, Math.min(limit, 1000));
        return jdbcTemplate.query(
                """
                        SELECT code, attempts
                        FROM link_cache_outbox
                        WHERE status = 'PENDING'
                          AND available_at <= NOW()
                        ORDER BY available_at ASC
                        LIMIT ?
                        """,
                (rs, rowNum) -> new PendingItem(
                        rs.getString("code"),
                        rs.getInt("attempts")
                ),
                n
        );
    }

    public void markDone(String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        jdbcTemplate.update(
                """
                        UPDATE link_cache_outbox
                        SET status = 'DONE',
                            processed_at = NOW(),
                            last_error = NULL
                        WHERE code = ?
                        """,
                code.trim()
        );
    }

    public void markRetry(String code, int attempts, String lastError, long delaySeconds) {
        if (code == null || code.isBlank()) {
            return;
        }
        int nextAttempts = Math.max(attempts, 1);
        long delay = Math.max(1, Math.min(delaySeconds, 3600));
        String err = lastError == null ? null : truncate(lastError, 512);
        jdbcTemplate.update(
                """
                        UPDATE link_cache_outbox
                        SET status = 'PENDING',
                            attempts = ?,
                            last_error = ?,
                            processed_at = NULL,
                            available_at = DATE_ADD(NOW(), INTERVAL ? SECOND)
                        WHERE code = ?
                        """,
                nextAttempts,
                err,
                delay,
                code.trim()
        );
    }

    public int deleteDoneOlderThanDays(int retentionDays, int limit) {
        int days = Math.max(retentionDays, 0);
        if (days <= 0) {
            return 0;
        }
        int n = Math.max(1, Math.min(limit, 50_000));
        return jdbcTemplate.update(
                """
                        DELETE FROM link_cache_outbox
                        WHERE status = 'DONE'
                          AND processed_at IS NOT NULL
                          AND processed_at < DATE_SUB(NOW(), INTERVAL ? DAY)
                        LIMIT ?
                        """,
                days,
                n
        );
    }

    public OutboxStats loadStats() {
        return jdbcTemplate.queryForObject(
                """
                        SELECT
                          COALESCE(SUM(status = 'PENDING'), 0) AS pending_total,
                          COALESCE(SUM(status = 'PENDING' AND available_at <= NOW()), 0) AS pending_ready,
                          COALESCE(
                            GREATEST(
                              0,
                              TIMESTAMPDIFF(
                                SECOND,
                                MIN(CASE WHEN status = 'PENDING' THEN available_at END),
                                NOW()
                              )
                            ),
                            0
                          ) AS pending_lag_seconds
                        FROM link_cache_outbox
                        """,
                (rs, rowNum) -> new OutboxStats(
                        rs.getLong("pending_total"),
                        rs.getLong("pending_ready"),
                        rs.getLong("pending_lag_seconds")
                )
        );
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
}
