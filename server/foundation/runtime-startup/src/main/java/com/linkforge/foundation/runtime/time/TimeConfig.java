package com.linkforge.foundation.runtime.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 提供统一的 UTC 时钟。
 *
 * <p>需要当前时间的应用服务应注入该 Bean，以便测试替换 Clock 并避免依赖服务器本地时区。</p>
 */
@Configuration
public class TimeConfig {

    /** 返回系统 UTC 时钟；持久化和业务窗口均以 UTC 为基准。 */
    @Bean
    public Clock systemUtcClock() {
        return Clock.systemUTC();
    }
}
