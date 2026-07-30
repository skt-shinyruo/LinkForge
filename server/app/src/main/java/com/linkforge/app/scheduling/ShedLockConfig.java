package com.linkforge.app.scheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 多实例定时任务的 Redis ShedLock 提供者。
 *
 * <p>锁命名空间固定为 {@code lf:shedlock}，默认最长持有 10 分钟。锁只减少同一作业并发执行，不提供
 * exactly-once；锁过期、进程故障或业务重试仍要求每个作业按幂等/可重放方式实现。</p>
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfig {

    /** 构造所有 ShedLock 注解共享的 Redis 锁提供者。 */
    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "lf:shedlock");
    }
}
