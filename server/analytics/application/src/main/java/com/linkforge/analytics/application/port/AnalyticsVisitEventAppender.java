package com.linkforge.analytics.application.port;

import com.linkforge.analytics.application.AnalyticsVisitEventService;

public interface AnalyticsVisitEventAppender {

    void append(AnalyticsVisitEventService.RedirectVisitEvent event);
}
