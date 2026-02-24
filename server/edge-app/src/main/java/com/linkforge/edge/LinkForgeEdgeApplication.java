package com.linkforge.edge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.linkforge")
public class LinkForgeEdgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkForgeEdgeApplication.class, args);
    }
}
