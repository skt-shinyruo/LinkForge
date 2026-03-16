package com.linkforge.analytics.infrastructure.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.LinkVisitEventMapper;
import com.linkforge.foundation.config.AnalyticsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * 访问明细留存清理作业：按 retentionDays 清理历史数据，避免明细表无限增长。
 */
@Component
public class AnalyticsEventRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventRetentionJob.class);

    private final LinkVisitEventMapper visitEventMapper;
    private final AnalyticsProperties analyticsProperties;

    public AnalyticsEventRetentionJob(LinkVisitEventMapper visitEventMapper, AnalyticsProperties analyticsProperties) {
        this.visitEventMapper = visitEventMapper;
        this.analyticsProperties = analyticsProperties;
    }

    @Scheduled(fixedDelayString = "${APP_ANALYTICS_EVENT_RETENTION_DELAY_MS:3600000}") // 1h
    @SchedulerLock(name = "lf:job:analytics:event-retention", lockAtMostFor = "PT30M")
    public void cleanup() {
        AnalyticsProperties.Events cfg = analyticsProperties == null ? null : analyticsProperties.getEvents();
        if (cfg == null || !cfg.isEnabled()) {
            return;
        }
        int days = cfg.getRetentionDays();
        if (days <= 0) {
            return;
        }

        // 分批删除，避免单次大事务造成锁竞争
        int total = 0;
        int loops = 0;
        while (loops < 20) {
            loops++;
            int deleted;
            try {
                deleted = visitEventMapper.deleteOld(days);
            } catch (DataAccessException e) {
                log.debug("cleanup visit events failed: err={}", e.getMessage());
                return;
            }
            total += deleted;
            if (deleted < 5000) {
                break;
            }
        }

        if (total > 0) {
            log.info("cleanup visit events done: deleted={}, retentionDays={}", total, days);
        }
    }
}
