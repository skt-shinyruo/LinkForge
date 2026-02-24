package com.linkforge.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.linkforge")
@EnableScheduling
@EnableJpaRepositories(basePackages = "com.linkforge")
@EntityScan(basePackages = "com.linkforge")
public class LinkForgeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkForgeApiApplication.class, args);
    }
}
