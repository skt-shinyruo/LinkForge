package com.linkforge.foundation.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    @Bean
    public Clock systemUtcClock() {
        return Clock.systemUTC();
    }
}

