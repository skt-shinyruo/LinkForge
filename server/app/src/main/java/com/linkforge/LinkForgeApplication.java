package com.linkforge;

import com.linkforge.app.compose.AccountsModule;
import com.linkforge.app.compose.AnalyticsModule;
import com.linkforge.app.compose.FoundationModule;
import com.linkforge.app.compose.RedirectModule;
import com.linkforge.app.compose.ShortlinkModule;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.config.CorsProperties;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.foundation.config.EdgeProperties;
import com.linkforge.foundation.config.IdProperties;
import com.linkforge.foundation.config.RedirectProperties;
import com.linkforge.foundation.config.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * LinkForge backend monolith entrypoint.
 *
 * <p>Combines API service and Redirect Edge into a single Spring Boot application.</p>
 */
@SpringBootApplication(scanBasePackages = "com.linkforge.app")
@Import({
        FoundationModule.class,
        AccountsModule.class,
        ShortlinkModule.class,
        RedirectModule.class,
        AnalyticsModule.class
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
@EnableJpaRepositories(basePackages = "com.linkforge")
@EntityScan(basePackages = "com.linkforge")
public class LinkForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkForgeApplication.class, args);
    }
}
