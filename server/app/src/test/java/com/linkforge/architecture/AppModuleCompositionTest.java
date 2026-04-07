package com.linkforge.architecture;

import com.linkforge.LinkForgeApplication;
import com.linkforge.app.compose.FoundationModule;
import com.linkforge.foundation.runtime.persistence.FoundationRuntimePersistenceModule;
import com.linkforge.foundation.runtime.security.FoundationRuntimeSecurityModule;
import com.linkforge.foundation.runtime.startup.FoundationRuntimeStartupModule;
import com.linkforge.foundation.runtime.tx.FoundationRuntimeTxModule;
import com.linkforge.foundation.runtime.web.FoundationRuntimeWebModule;
import com.linkforge.governance.interfaces.web.ApprovalController;
import com.linkforge.governance.interfaces.web.AuditController;
import com.linkforge.platform.interfaces.web.TenantAdminApplicationController;
import com.linkforge.platform.interfaces.web.TenantAdminDomainController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class AppModuleCompositionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LinkForgeApplication.class, ForceLazyBeansTestConfig.class)
            .withPropertyValues(
                    "spring.main.web-application-type=none",
                    "spring.autoconfigure.exclude="
                            + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                            + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
            );

    @Test
    void app_bootstrap_should_include_platform_and_governance_controllers() {
        contextRunner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context.getBeanNamesForType(TenantAdminApplicationController.class)).isNotEmpty();
            assertThat(context.getBeanNamesForType(TenantAdminDomainController.class)).isNotEmpty();
            assertThat(context.getBeanNamesForType(ApprovalController.class)).isNotEmpty();
            assertThat(context.getBeanNamesForType(AuditController.class)).isNotEmpty();
        });
    }

    @Test
    void foundation_module_should_import_explicit_runtime_modules_instead_of_scanning_foundation() {
        assertThat(FoundationModule.class.getAnnotation(ComponentScan.class)).isNull();

        Import importAnnotation = FoundationModule.class.getAnnotation(Import.class);
        assertThat(importAnnotation).isNotNull();
        assertThat(importAnnotation.value()).containsExactlyInAnyOrder(
                FoundationRuntimeWebModule.class,
                FoundationRuntimeSecurityModule.class,
                FoundationRuntimePersistenceModule.class,
                FoundationRuntimeTxModule.class,
                FoundationRuntimeStartupModule.class
        );
    }

    @Configuration(proxyBeanMethods = false)
    static class ForceLazyBeansTestConfig {
        @Bean
        static BeanFactoryPostProcessor forceAllBeanDefinitionsLazy() {
            return beanFactory -> {
                for (String beanName : beanFactory.getBeanDefinitionNames()) {
                    beanFactory.getBeanDefinition(beanName).setLazyInit(true);
                }
            };
        }
    }
}
