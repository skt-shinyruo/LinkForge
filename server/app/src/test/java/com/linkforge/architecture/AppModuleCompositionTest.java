package com.linkforge.architecture;

import com.linkforge.LinkForgeApplication;
import com.linkforge.accounts.application.AccountsApplicationConfig;
import com.linkforge.accounts.infrastructure.AccountsInfrastructureConfig;
import com.linkforge.accounts.interfaces.AccountsInterfacesConfig;
import com.linkforge.analytics.application.AnalyticsApplicationConfig;
import com.linkforge.analytics.infrastructure.AnalyticsInfrastructureConfig;
import com.linkforge.analytics.interfaces.AnalyticsInterfacesConfig;
import com.linkforge.app.compose.AccountsModule;
import com.linkforge.app.compose.AnalyticsModule;
import com.linkforge.app.compose.FoundationModule;
import com.linkforge.app.compose.GovernanceModule;
import com.linkforge.app.compose.PlatformModule;
import com.linkforge.app.compose.RedirectModule;
import com.linkforge.app.compose.ShortlinkModule;
import com.linkforge.foundation.runtime.persistence.FoundationRuntimePersistenceModule;
import com.linkforge.foundation.runtime.security.FoundationRuntimeSecurityModule;
import com.linkforge.foundation.runtime.startup.FoundationRuntimeStartupModule;
import com.linkforge.foundation.runtime.tx.FoundationRuntimeTxModule;
import com.linkforge.foundation.runtime.web.FoundationRuntimeWebModule;
import com.linkforge.governance.application.GovernanceApplicationConfig;
import com.linkforge.governance.infrastructure.GovernanceInfrastructureConfig;
import com.linkforge.governance.interfaces.GovernanceInterfacesConfig;
import com.linkforge.governance.interfaces.web.ApprovalController;
import com.linkforge.governance.interfaces.web.AuditController;
import com.linkforge.platform.application.PlatformApplicationConfig;
import com.linkforge.platform.infrastructure.PlatformInfrastructureConfig;
import com.linkforge.platform.interfaces.PlatformInterfacesConfig;
import com.linkforge.platform.interfaces.web.TenantAdminApplicationController;
import com.linkforge.platform.interfaces.web.TenantAdminDomainController;
import com.linkforge.redirect.application.RedirectApplicationConfig;
import com.linkforge.redirect.infrastructure.RedirectInfrastructureConfig;
import com.linkforge.redirect.interfaces.RedirectInterfacesConfig;
import com.linkforge.shortlink.application.ShortlinkApplicationConfig;
import com.linkforge.shortlink.infrastructure.ShortlinkInfrastructureConfig;
import com.linkforge.shortlink.interfaces.ShortlinkInterfacesConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

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
    void app_should_import_explicit_context_modules_only() {
        Import importAnnotation = LinkForgeApplication.class.getAnnotation(Import.class);
        assertThat(importAnnotation).isNotNull();
        assertThat(importAnnotation.value()).containsExactlyInAnyOrder(
                FoundationModule.class,
                AccountsModule.class,
                ShortlinkModule.class,
                RedirectModule.class,
                AnalyticsModule.class,
                PlatformModule.class,
                GovernanceModule.class
        );
    }

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
        assertExplicitImports(
                FoundationModule.class,
                FoundationRuntimeWebModule.class,
                FoundationRuntimeSecurityModule.class,
                FoundationRuntimePersistenceModule.class,
                FoundationRuntimeTxModule.class,
                FoundationRuntimeStartupModule.class
        );
    }

    @Test
    void accounts_module_should_import_explicit_layer_configs() {
        assertExplicitImports(
                AccountsModule.class,
                AccountsApplicationConfig.class,
                AccountsInfrastructureConfig.class,
                AccountsInterfacesConfig.class
        );
    }

    @Test
    void shortlink_module_should_import_explicit_layer_configs() {
        assertExplicitImports(
                ShortlinkModule.class,
                ShortlinkApplicationConfig.class,
                ShortlinkInfrastructureConfig.class,
                ShortlinkInterfacesConfig.class
        );
    }

    @Test
    void redirect_module_should_import_explicit_layer_configs() {
        assertExplicitImports(
                RedirectModule.class,
                RedirectApplicationConfig.class,
                RedirectInfrastructureConfig.class,
                RedirectInterfacesConfig.class
        );
    }

    @Test
    void analytics_module_should_import_explicit_layer_configs() {
        assertExplicitImports(
                AnalyticsModule.class,
                AnalyticsApplicationConfig.class,
                AnalyticsInfrastructureConfig.class,
                AnalyticsInterfacesConfig.class
        );
    }

    @Test
    void platform_module_should_import_explicit_layer_configs() {
        assertExplicitImports(
                PlatformModule.class,
                PlatformApplicationConfig.class,
                PlatformInfrastructureConfig.class,
                PlatformInterfacesConfig.class
        );
    }

    @Test
    void governance_module_should_import_explicit_layer_configs() {
        assertExplicitImports(
                GovernanceModule.class,
                GovernanceApplicationConfig.class,
                GovernanceInfrastructureConfig.class,
                GovernanceInterfacesConfig.class
        );
    }

    private static void assertExplicitImports(Class<?> moduleClass, Class<?>... expectedImports) {
        assertThat(moduleClass.getAnnotation(ComponentScan.class)).isNull();

        Import importAnnotation = moduleClass.getAnnotation(Import.class);
        assertThat(importAnnotation).isNotNull();
        assertThat(importAnnotation.value()).containsExactlyInAnyOrder(expectedImports);
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
