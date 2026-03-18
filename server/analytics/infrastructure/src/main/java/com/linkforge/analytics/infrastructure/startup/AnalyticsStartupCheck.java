package com.linkforge.analytics.infrastructure.startup;

import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.config.StartupValidation;
import com.linkforge.foundation.runtime.startup.StartupCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AnalyticsStartupCheck implements StartupCheck {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsStartupCheck.class);

    private final AnalyticsProperties analyticsProperties;

    public AnalyticsStartupCheck(AnalyticsProperties analyticsProperties) {
        this.analyticsProperties = analyticsProperties;
    }

    @Override
    public void validate(boolean strict, List<String> errors) {
        StartupValidation.validateAnalyticsBasics(analyticsProperties, strict, log, errors);
        try {
            StartupValidation.validateAnalyticsTrackingAllowlist(analyticsProperties, errors);
            StartupValidation.validateAnalyticsDimensionsTypes(analyticsProperties, errors);
            StartupValidation.validateAnalyticsEvents(analyticsProperties, errors);
        } catch (Exception e) {
            errors.add("analytics 配置校验异常: " + e.getMessage());
        }
    }
}
