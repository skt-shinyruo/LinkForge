package com.linkforge.api;

import com.linkforge.platform.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.linkforge")
@EnableConfigurationProperties(AppProperties.class)
@EnableJpaRepositories(basePackages = "com.linkforge")
@EntityScan(basePackages = "com.linkforge")
public class LinkForgeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkForgeApiApplication.class, args);
    }
}
