package com.linkforge.app.config;

import com.linkforge.foundation.config.IdProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdConfig {

    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(IdProperties properties) {
        long workerId = properties == null ? 1L : properties.getWorkerId();
        long datacenterId = properties == null ? 1L : properties.getDatacenterId();
        return new SnowflakeIdGenerator(workerId, datacenterId);
    }
}
