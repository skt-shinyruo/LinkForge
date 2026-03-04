package com.linkforge.edge;

import com.linkforge.platform.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.linkforge")
@EnableConfigurationProperties(AppProperties.class)
public class LinkForgeEdgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkForgeEdgeApplication.class, args);
    }
}
