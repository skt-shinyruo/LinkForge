package com.linkforge.foundation.runtime.persistence;

import com.linkforge.foundation.runtime.eventing.MybatisIntegrationCheckpointRepository;
import com.linkforge.foundation.runtime.eventing.MybatisIntegrationDeadLetterRepository;
import com.linkforge.foundation.runtime.eventing.MybatisIntegrationEventStore;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Foundation 集成事件存储的持久化运行时导出模块。
 *
 * <p>只导出共享 outbox/checkpoint/DLQ 所需 mapper 和适配器；各业务上下文仍由自己的配置类扫描和注册 mapper。</p>
 */
@Configuration(proxyBeanMethods = false)
@Import({
        IntegrationEventMybatisConfig.class,
        MybatisIntegrationCheckpointRepository.class,
        MybatisIntegrationDeadLetterRepository.class,
        MybatisIntegrationEventStore.class
})
public class FoundationRuntimePersistenceModule {
}
