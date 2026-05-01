package com.linkforge.analytics.application;

import com.linkforge.analytics.application.port.AnalyticsVisitEventAppender;
import com.linkforge.analytics.domain.VisitDimension;
import com.linkforge.analytics.domain.VisitNormalizationPolicy;
import com.linkforge.contract.analytics.RedirectVisitRecord;
import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.contract.analytics.VisitRecorderPort;
import com.linkforge.foundation.config.AnalyticsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AnalyticsVisitEventService implements VisitRecorderPort {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsVisitEventService.class);

    private final AnalyticsVisitEventAppender appender;
    private final AnalyticsProperties analyticsProperties;
    private final VisitNormalizationPolicy visitNormalizationPolicy;

    public AnalyticsVisitEventService(AnalyticsVisitEventAppender appender) {
        this(appender, null);
    }

    @Autowired
    public AnalyticsVisitEventService(AnalyticsVisitEventAppender appender, AnalyticsProperties analyticsProperties) {
        this(appender, analyticsProperties, new VisitNormalizationPolicy());
    }

    AnalyticsVisitEventService(
            AnalyticsVisitEventAppender appender,
            AnalyticsProperties analyticsProperties,
            VisitNormalizationPolicy visitNormalizationPolicy
    ) {
        this.appender = appender;
        this.analyticsProperties = analyticsProperties;
        this.visitNormalizationPolicy = visitNormalizationPolicy;
    }

    @Override
    public void recordVisit(RedirectVisitRecord visit) {
        if (visit == null) {
            return;
        }
        VisitContext context = visit.visitContext();
        VisitDimension dimension = visitNormalizationPolicy.normalize(
                context == null ? null : context.ip(),
                context == null ? null : context.userAgent(),
                context == null ? null : context.referer(),
                context == null ? null : context.acceptLanguage(),
                context == null ? null : context.trackingParams()
        );
        append(new RedirectVisitEvent(
                visit.tenantId(),
                visit.linkId(),
                visit.occurredAtMillis(),
                visit.applicationId(),
                visit.domainId(),
                visit.code(),
                visit.originalUrl(),
                dimension.ip(),
                dimension.userAgent(),
                dimension.referer(),
                dimension.acceptLanguage(),
                dimension.trackingParams()
        ));
    }

    public void append(RedirectVisitEvent event) {
        if (event == null || appender == null) {
            return;
        }
        try {
            appender.append(event);
        } catch (RuntimeException e) {
            if (!isFailOpen()) {
                throw e;
            }
            log.debug(
                    "append analytics visit event failed: tenantId={}, linkId={}, code={}, err={}",
                    event.tenantId(),
                    event.linkId(),
                    event.code(),
                    e.getMessage()
            );
        }
    }

    private boolean isFailOpen() {
        AnalyticsProperties.Events cfg = analyticsProperties == null ? null : analyticsProperties.getEvents();
        return cfg == null || cfg.isFailOpen();
    }

    public record RedirectVisitEvent(
            long tenantId,
            long linkId,
            long occurredAtMillis,
            Long applicationId,
            Long domainId,
            String code,
            String originalUrl,
            String ip,
            String userAgent,
            String referer,
            String acceptLanguage,
            Map<String, String> trackingParams
    ) {

        public RedirectVisitEvent {
            trackingParams = trackingParams == null ? Map.of() : Map.copyOf(trackingParams);
        }
    }
}
