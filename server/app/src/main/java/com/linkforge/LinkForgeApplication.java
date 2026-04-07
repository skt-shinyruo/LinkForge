package com.linkforge;

import com.linkforge.accounts.interfaces.AccountsRuntimeModule;
import com.linkforge.analytics.interfaces.AnalyticsRuntimeModule;
import com.linkforge.foundation.runtime.FoundationRuntimeModule;
import com.linkforge.governance.interfaces.GovernanceRuntimeModule;
import com.linkforge.platform.interfaces.PlatformRuntimeModule;
import com.linkforge.redirect.interfaces.RedirectRuntimeModule;
import com.linkforge.shortlink.interfaces.ShortlinkRuntimeModule;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.config.CorsProperties;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.foundation.config.EdgeProperties;
import com.linkforge.foundation.config.IdProperties;
import com.linkforge.foundation.config.RedirectProperties;
import com.linkforge.foundation.config.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * LinkForge backend monolith entrypoint.
 *
 * <p>Combines API service and Redirect Edge into a single Spring Boot application.</p>
 */
@SpringBootApplication(scanBasePackages = {
        "com.linkforge.app.api",
        "com.linkforge.app.config",
        "com.linkforge.app.scheduling",
        "com.linkforge.app.security",
        "com.linkforge.app.startup"
})
@Import({
        FoundationRuntimeModule.class,
        AccountsRuntimeModule.class,
        ShortlinkRuntimeModule.class,
        RedirectRuntimeModule.class,
        AnalyticsRuntimeModule.class,
        PlatformRuntimeModule.class,
        GovernanceRuntimeModule.class
})
@EnableConfigurationProperties({
        CoreProperties.class,
        IdProperties.class,
        SecurityProperties.class,
        CorsProperties.class,
        RedirectProperties.class,
        AnalyticsProperties.class,
        EdgeProperties.class
})
public class LinkForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkForgeApplication.class, args);
    }
}
