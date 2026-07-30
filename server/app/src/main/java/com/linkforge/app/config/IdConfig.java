package com.linkforge.app.config;

import com.linkforge.foundation.config.IdProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 将部署配置绑定为进程内 Snowflake ID 生成器。
 *
 * <p>属性对象通常由应用入口启用；空属性仅保留直接测试/极简容器的 1/1 兼容回退。生产节点唯一性由启动检查
 * 和部署分配共同保证，而不是由此 Bean 自动协调。</p>
 */
@Configuration
public class IdConfig {

    /** 使用 {@code app.id.worker-id/datacenter-id} 创建单例生成器。 */
    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(IdProperties properties) {
        long workerId = properties == null ? 1L : properties.getWorkerId();
        long datacenterId = properties == null ? 1L : properties.getDatacenterId();
        return new SnowflakeIdGenerator(workerId, datacenterId);
    }
}
