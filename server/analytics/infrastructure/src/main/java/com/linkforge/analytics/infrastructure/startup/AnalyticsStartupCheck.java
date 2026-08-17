package com.linkforge.analytics.infrastructure.startup;

import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.config.StartupValidation;
import com.linkforge.foundation.runtime.startup.StartupCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Analytics 配置的启动期校验入口。
 *
 * <p>复用 foundation 的统一校验规则检查盐和 Redis 生命周期。校验只收集错误；是否阻止应用启动由外层
 * {@code strict} 策略决定。</p>
 */
@Component
public class AnalyticsStartupCheck implements StartupCheck {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsStartupCheck.class);

    private final AnalyticsProperties analyticsProperties;

    public AnalyticsStartupCheck(AnalyticsProperties analyticsProperties) {
        this.analyticsProperties = analyticsProperties;
    }

    /** 将 Analytics 配置问题附加到共享错误列表，而非抛出未分类异常。 */
    @Override
    public void validate(boolean strict, List<String> errors) {
        StartupValidation.validateAnalyticsBasics(analyticsProperties, strict, log, errors);
    }
}
