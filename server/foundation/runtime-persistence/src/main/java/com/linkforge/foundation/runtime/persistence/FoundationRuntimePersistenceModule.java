package com.linkforge.foundation.runtime.persistence;

import com.linkforge.foundation.runtime.eventing.MybatisIntegrationCheckpointRepository;
import com.linkforge.foundation.runtime.eventing.MybatisIntegrationDeadLetterRepository;
import com.linkforge.foundation.runtime.eventing.MybatisIntegrationEventStore;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        IntegrationEventMybatisConfig.class,
        MybatisIntegrationCheckpointRepository.class,
        MybatisIntegrationDeadLetterRepository.class,
        MybatisIntegrationEventStore.class
})
public class FoundationRuntimePersistenceModule {
}
