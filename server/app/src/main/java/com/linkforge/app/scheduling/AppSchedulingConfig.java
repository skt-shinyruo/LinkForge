package com.linkforge.app.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用内定时任务总开关。
 *
 * <p>{@code app.scheduling.enabled} 缺省为 true；关闭后所有 {@code @Scheduled} 作业停止触发，但不会撤销
 * 已持久化的 outbox、Redis marker 或历史数据，恢复后由各作业自身的追赶/重试语义处理。</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AppSchedulingConfig {
}
