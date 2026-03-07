package com.linkforge.api.config;

import com.linkforge.platform.config.AppProperties;
import com.linkforge.platform.id.SnowflakeIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiIdConfig {

    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(AppProperties properties) {
        AppProperties.Id id = properties.getId();
        long workerId = id == null ? 1L : id.getWorkerId();
        long datacenterId = id == null ? 1L : id.getDatacenterId();
        return new SnowflakeIdGenerator(workerId, datacenterId);
    }
}

