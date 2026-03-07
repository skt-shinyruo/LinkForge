package com.linkforge;

import com.linkforge.foundation.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * LinkForge backend monolith entrypoint.
 *
 * <p>Combines API service and Redirect Edge into a single Spring Boot application.</p>
 */
@SpringBootApplication(scanBasePackages = "com.linkforge")
@EnableConfigurationProperties(AppProperties.class)
@EnableJpaRepositories(basePackages = "com.linkforge")
@EntityScan(basePackages = "com.linkforge")
public class LinkForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkForgeApplication.class, args);
    }
}
